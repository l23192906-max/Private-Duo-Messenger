import { Router, type IRouter } from "express";
import {
  authenticateRequest,
  requireAdmin,
  type AuthenticatedRequest,
} from "../our-chat/auth";
import {
  activityHistory,
  authenticateUsername,
  connectionHistory,
  connectionStatistics,
  createMessage,
  getGuestSnapshot,
  getStatusButtonVisible,
  initializeAccounts,
  listEvents,
  listMessages,
  markMessageDelivered,
  markMessageRead,
  recordEvent,
  renameGuest,
  setSharingActive,
  setStatusButton,
  setupStatus,
  updateHeartbeat,
  type InternetStatus,
  type Role,
} from "../our-chat/store";
import {
  emitToOtherRole,
  emitToRole,
  isRoleOnline,
} from "../our-chat/realtime";

const router: IRouter = Router();
const allowedGuestEvents = new Set([
  "permissions_revoked",
  "monitoring_service_stopped",
  "monitoring_service_recovered",
  "server_connection_lost",
  "server_connection_restored",
  "device_restarted",
  "battery_restriction_detected",
]);

const cleanUsername = (value: unknown) =>
  typeof value === "string" ? value.trim() : "";
const validUsername = (value: string) =>
  value.length >= 3 &&
  value.length <= 24 &&
  /^[a-zA-Z0-9._-]+$/.test(value);
const currentRole = (req: AuthenticatedRequest): Role => req.chatRole;
const emitLatestEvents = (afterId: number) => {
  for (const event of listEvents(100)) {
    if (event.id > afterId) emitToRole("ADMIN", "event:new", event);
  }
};
const newestEventId = () => listEvents(1)[0]?.id ?? 0;

router.get("/our-chat/setup/status", (_req, res) => {
  res.json(setupStatus());
});

router.post("/our-chat/setup", (req, res) => {
  const adminUsername = cleanUsername(req.body?.adminUsername);
  const guestUsername = cleanUsername(req.body?.guestUsername);
  if (!validUsername(adminUsername) || !validUsername(guestUsername)) {
    res.status(400).json({
      error: "Usernames must be 3–24 letters, numbers, dots, dashes, or underscores.",
    });
    return;
  }
  if (adminUsername.toLocaleLowerCase() === guestUsername.toLocaleLowerCase()) {
    res.status(400).json({ error: "ADMIN and GUEST need different usernames." });
    return;
  }
  try {
    res.status(201).json(initializeAccounts(adminUsername, guestUsername));
  } catch (error) {
    res.status(409).json({
      error: error instanceof Error ? error.message : "Setup could not be completed.",
    });
  }
});

router.post("/our-chat/login", (req, res) => {
  const username = cleanUsername(req.body?.username);
  if (!username || username.length > 24) {
    res.status(400).json({ error: "Enter one of the configured usernames." });
    return;
  }
  try {
    res.json(authenticateUsername(username));
  } catch (error) {
    res.status(401).json({
      error: error instanceof Error ? error.message : "Unable to sign in.",
    });
  }
});

router.get("/our-chat/state", authenticateRequest, (req, res) => {
  const user = (req as AuthenticatedRequest).chatRole;
  const accounts = setupStatus();
  res.json({
    role: user,
    accounts,
    peerOnline: isRoleOnline(user === "ADMIN" ? "GUEST" : "ADMIN"),
    showStatusButton: getStatusButtonVisible(),
    guestStatus: getGuestSnapshot(),
  });
});

router.get("/our-chat/messages", authenticateRequest, (req, res) => {
  const beforeValue = Number(req.query["before"]);
  const limitValue = Number(req.query["limit"] ?? 100);
  const limit = Number.isFinite(limitValue)
    ? Math.min(200, Math.max(1, Math.floor(limitValue)))
    : 100;
  const before =
    Number.isFinite(beforeValue) && beforeValue > 0
      ? Math.floor(beforeValue)
      : undefined;
  const role = currentRole(req as AuthenticatedRequest);
  const messages = listMessages(limit, before);
  for (const message of messages) {
    if (message.sender !== role && !message.deliveredAt) {
      const updated = markMessageDelivered(message.id);
      if (updated) emitToOtherRole(role, "message:updated", updated);
    }
  }
  res.json({ messages: listMessages(limit, before) });
});

router.post("/our-chat/messages", authenticateRequest, (req, res) => {
  const body = typeof req.body?.body === "string" ? req.body.body.trim() : "";
  const clientId =
    typeof req.body?.clientId === "string"
      ? req.body.clientId.slice(0, 80)
      : undefined;
  if (!body || body.length > 4000) {
    res.status(400).json({ error: "Message text must be between 1 and 4,000 characters." });
    return;
  }
  const sender = currentRole(req as AuthenticatedRequest);
  const recipient: Role = sender === "ADMIN" ? "GUEST" : "ADMIN";
  let message = createMessage(sender, body, clientId);
  if (isRoleOnline(recipient)) {
    const delivered = markMessageDelivered(message.id);
    if (delivered) message = { ...delivered, recipient };
  }
  emitToRole(recipient, "message:new", message);
  emitToRole(sender, "message:new", message);
  if (message.deliveredAt) emitToRole(sender, "message:updated", message);
  res.status(201).json({ message });
});

router.post("/our-chat/messages/:id/read", authenticateRequest, (req, res) => {
  const id = Number(req.params["id"]);
  if (!Number.isInteger(id) || id <= 0) {
    res.status(400).json({ error: "Invalid message id." });
    return;
  }
  const role = currentRole(req as AuthenticatedRequest);
  const message = markMessageRead(id, role);
  if (!message) {
    res.status(404).json({ error: "Message not found or not addressed to this account." });
    return;
  }
  emitToOtherRole(role, "message:updated", message);
  res.json({ message });
});

router.put(
  "/our-chat/admin/guest-username",
  authenticateRequest,
  requireAdmin,
  (req, res) => {
    const guestUsername = cleanUsername(req.body?.guestUsername);
    const adminUsername = setupStatus().adminUsername;
    if (!validUsername(guestUsername)) {
      res.status(400).json({
        error: "Usernames must be 3–24 letters, numbers, dots, dashes, or underscores.",
      });
      return;
    }
    if (
      guestUsername.toLocaleLowerCase() === adminUsername?.toLocaleLowerCase()
    ) {
      res.status(400).json({ error: "The usernames must be different." });
      return;
    }
    const result = renameGuest(guestUsername);
    const event = listEvents(1)[0];
    if (event) emitToRole("ADMIN", "event:new", event);
    emitToRole("GUEST", "state:update", { accounts: result });
    res.json(result);
  },
);

router.put(
  "/our-chat/admin/status-button",
  authenticateRequest,
  requireAdmin,
  (req, res) => {
    if (typeof req.body?.visible !== "boolean") {
      res.status(400).json({ error: "visible must be true or false." });
      return;
    }
    const result = setStatusButton(req.body.visible);
    const event = listEvents(1)[0];
    if (event) emitToRole("ADMIN", "event:new", event);
    emitToRole("GUEST", "control:update", {
      showStatusButton: result.showStatusButton,
    });
    res.json(result);
  },
);

router.post(
  "/our-chat/monitoring/consent",
  authenticateRequest,
  (req, res) => {
    const role = currentRole(req as AuthenticatedRequest);
    if (role !== "GUEST") {
      res.status(403).json({ error: "Only GUEST can grant or revoke sharing consent." });
      return;
    }
    if (typeof req.body?.active !== "boolean") {
      res.status(400).json({ error: "active must be true or false." });
      return;
    }
    const previousId = newestEventId();
    const snapshot = setSharingActive(req.body.active);
    emitLatestEvents(previousId);
    emitToRole("ADMIN", "monitoring:update", snapshot);
    emitToRole("GUEST", "monitoring:update", snapshot);
    res.json(snapshot);
  },
);

router.post(
  "/our-chat/monitoring/heartbeat",
  authenticateRequest,
  (req, res) => {
    const role = currentRole(req as AuthenticatedRequest);
    if (role !== "GUEST") {
      res.status(403).json({ error: "Only the GUEST device can report its status." });
      return;
    }
    const internetStatus = req.body?.internetStatus;
    const networkType =
      typeof req.body?.networkType === "string"
        ? req.body.networkType.slice(0, 40)
        : "unknown";
    if (!["connected", "disconnected", "unknown"].includes(internetStatus)) {
      res.status(400).json({ error: "Invalid internetStatus." });
      return;
    }
    if (
      typeof req.body?.messengerForeground !== "boolean" ||
      typeof req.body?.serviceActive !== "boolean"
    ) {
      res.status(400).json({ error: "Heartbeat is missing foreground or service status." });
      return;
    }
    const snapshot = setSharingActiveForHeartbeat(
      (req.body?.sharingActive as boolean | undefined) === true,
    );
    if (!snapshot) {
      res.status(409).json({ error: "Monitoring consent is not active." });
      return;
    }
    const previousId = newestEventId();
    const updated = updateHeartbeat({
      internetStatus: internetStatus as InternetStatus,
      networkType,
      messengerForeground: req.body.messengerForeground,
      serviceActive: req.body.serviceActive,
      permissions:
        req.body?.permissions &&
        typeof req.body.permissions === "object" &&
        !Array.isArray(req.body.permissions)
          ? req.body.permissions as Record<string, unknown>
          : {},
    });
    emitLatestEvents(previousId);
    emitToRole("ADMIN", "monitoring:update", updated);
    emitToRole("GUEST", "monitoring:update", updated);
    res.json({ receivedAt: new Date().toISOString(), state: updated.state });
  },
);

router.post(
  "/our-chat/monitoring/event",
  authenticateRequest,
  (req, res) => {
    const role = currentRole(req as AuthenticatedRequest);
    const type = typeof req.body?.type === "string" ? req.body.type : "";
    if (role !== "GUEST" || !allowedGuestEvents.has(type)) {
      res.status(403).json({ error: "This monitoring event is not allowed." });
      return;
    }
    const detail =
      req.body?.detail &&
      typeof req.body.detail === "object" &&
      !Array.isArray(req.body.detail)
        ? req.body.detail as Record<string, unknown>
        : {};
    const event = recordEvent(type, detail);
    emitToRole("ADMIN", "event:new", event);
    res.status(201).json({ event });
  },
);

function setSharingActiveForHeartbeat(active: boolean) {
  const before = getGuestSnapshot();
  if (!before.sharingActive || !active) return null;
  return before;
}

router.get(
  "/our-chat/admin/dashboard",
  authenticateRequest,
  requireAdmin,
  (_req, res) => {
    res.json({
      guestStatus: getGuestSnapshot(),
      online: isRoleOnline("GUEST"),
      connection: {
        day: connectionStatistics("day"),
        week: connectionStatistics("week"),
      },
      activityHistory: activityHistory(),
      connectionHistory: connectionHistory(),
      events: listEvents(150),
      diagnostics: getGuestSnapshot().permissions,
    });
  },
);

router.get(
  "/our-chat/admin/events",
  authenticateRequest,
  requireAdmin,
  (req, res) => {
    const limit = Math.min(300, Math.max(1, Number(req.query["limit"]) || 100));
    res.json({ events: listEvents(limit) });
  },
);

export default router;
