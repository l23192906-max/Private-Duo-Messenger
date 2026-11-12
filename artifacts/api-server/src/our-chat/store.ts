import { createHash, randomBytes } from "node:crypto";
import { mkdirSync } from "node:fs";
import path from "node:path";
import { DatabaseSync } from "node:sqlite";

export type Role = "ADMIN" | "GUEST";
export type InternetStatus = "connected" | "disconnected" | "unknown";
export type MonitorState =
  | "ONLINE"
  | "OFFLINE_CONFIRMED"
  | "UNKNOWN"
  | "STALE";

export type GuestSnapshot = {
  sharingActive: boolean;
  internetStatus: InternetStatus;
  serverConnectivity: "connected" | "unknown" | "stale";
  networkType: string;
  messengerForeground: boolean | null;
  serviceActive: boolean;
  permissions: Record<string, unknown>;
  lastUpdate: string | null;
  state: MonitorState;
};

const dataDirectory =
  process.env["OUR_CHAT_DATA_DIR"] ?? path.resolve(process.cwd(), "data");
mkdirSync(dataDirectory, { recursive: true });

const databasePath =
  process.env["OUR_CHAT_DB_PATH"] ??
  path.join(dataDirectory, "our-chat.sqlite");
const db = new DatabaseSync(databasePath);

db.exec(`
  PRAGMA journal_mode = WAL;
  PRAGMA foreign_keys = ON;

  CREATE TABLE IF NOT EXISTS settings (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    admin_username TEXT NOT NULL,
    guest_username TEXT NOT NULL,
    show_status_button INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS sessions (
    token_hash TEXT PRIMARY KEY,
    role TEXT NOT NULL CHECK (role IN ('ADMIN', 'GUEST')),
    created_at TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id TEXT UNIQUE,
    sender TEXT NOT NULL CHECK (sender IN ('ADMIN', 'GUEST')),
    body TEXT NOT NULL,
    created_at TEXT NOT NULL,
    delivered_at TEXT,
    read_at TEXT
  );

  CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL,
    detail TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS connection_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    started_at TEXT NOT NULL,
    ended_at TEXT
  );

  CREATE TABLE IF NOT EXISTS activity_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    started_at TEXT NOT NULL,
    ended_at TEXT
  );

  CREATE TABLE IF NOT EXISTS monitor_snapshot (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    sharing_active INTEGER NOT NULL DEFAULT 0,
    internet_status TEXT NOT NULL DEFAULT 'unknown',
    network_type TEXT NOT NULL DEFAULT 'unknown',
    messenger_foreground INTEGER,
    service_active INTEGER NOT NULL DEFAULT 0,
    permissions TEXT NOT NULL DEFAULT '{}',
    last_update TEXT
  );

  INSERT OR IGNORE INTO monitor_snapshot(id) VALUES (1);
`);

const now = () => new Date().toISOString();
const tokenDigest = (token: string) =>
  createHash("sha256").update(token).digest("hex");
const parseJson = <T>(value: unknown, fallback: T): T => {
  if (typeof value !== "string") return fallback;
  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
};

export function setupStatus() {
  const row = db
    .prepare(
      "SELECT admin_username, guest_username, show_status_button FROM settings WHERE id = 1",
    )
    .get() as
    | {
        admin_username: string;
        guest_username: string;
        show_status_button: number;
      }
    | undefined;
  return row
    ? {
        configured: true,
        adminUsername: row.admin_username,
        guestUsername: row.guest_username,
        showStatusButton: Boolean(row.show_status_button),
      }
    : {
        configured: false,
        adminUsername: null,
        guestUsername: null,
        showStatusButton: false,
      };
}

export function initializeAccounts(adminUsername: string, guestUsername: string) {
  if (setupStatus().configured) {
    throw new Error("OUR CHAT has already been initialized.");
  }
  db.prepare(
    "INSERT INTO settings(id, admin_username, guest_username, created_at) VALUES (1, ?, ?, ?)",
  ).run(adminUsername, guestUsername, now());
  recordEvent("accounts_initialized", {
    adminUsername,
    guestUsername,
  });
  return setupStatus();
}

export function authenticateUsername(username: string) {
  const settings = setupStatus();
  if (!settings.configured) throw new Error("OUR CHAT has not been initialized.");

  const role =
    username.toLocaleLowerCase() ===
    (settings.adminUsername as string).toLocaleLowerCase()
      ? "ADMIN"
      : username.toLocaleLowerCase() ===
          (settings.guestUsername as string).toLocaleLowerCase()
        ? "GUEST"
        : null;
  if (!role) throw new Error("That username is not one of the two accounts.");

  const token = randomBytes(32).toString("base64url");
  db.prepare("INSERT INTO sessions(token_hash, role, created_at) VALUES (?, ?, ?)").run(
    tokenDigest(token),
    role,
    now(),
  );
  return { token, role, ...setupStatus() };
}

export function roleForToken(token: string): Role | null {
  const row = db
    .prepare("SELECT role FROM sessions WHERE token_hash = ?")
    .get(tokenDigest(token)) as { role: Role } | undefined;
  return row?.role ?? null;
}

export function renameGuest(username: string) {
  db.prepare("UPDATE settings SET guest_username = ? WHERE id = 1").run(username);
  recordEvent("guest_username_changed", { guestUsername: username });
  return setupStatus();
}

export function setStatusButton(show: boolean) {
  db.prepare("UPDATE settings SET show_status_button = ? WHERE id = 1").run(
    show ? 1 : 0,
  );
  recordEvent("status_button_changed", { visible: show });
  return setupStatus();
}

export function getStatusButtonVisible() {
  const row = db
    .prepare("SELECT show_status_button FROM settings WHERE id = 1")
    .get() as { show_status_button: number } | undefined;
  return Boolean(row?.show_status_button);
}

export function createMessage(sender: Role, body: string, clientId?: string) {
  const recipient: Role = sender === "ADMIN" ? "GUEST" : "ADMIN";
  const existing = clientId
    ? (db
        .prepare("SELECT * FROM messages WHERE client_id = ?")
        .get(clientId) as Record<string, unknown> | undefined)
    : undefined;
  if (existing) return { ...mapMessage(existing), recipient };

  const createdAt = now();
  const result = db
    .prepare(
      "INSERT INTO messages(client_id, sender, body, created_at) VALUES (?, ?, ?, ?)",
    )
    .run(clientId ?? null, sender, body, createdAt);
  return {
    id: Number(result.lastInsertRowid),
    clientId: clientId ?? null,
    sender,
    body,
    createdAt,
    deliveredAt: null,
    readAt: null,
    recipient,
  };
}

function mapMessage(row: Record<string, unknown>) {
  return {
    id: Number(row["id"]),
    clientId: (row["client_id"] as string | null) ?? null,
    sender: row["sender"] as Role,
    body: String(row["body"]),
    createdAt: String(row["created_at"]),
    deliveredAt: (row["delivered_at"] as string | null) ?? null,
    readAt: (row["read_at"] as string | null) ?? null,
  };
}

export function listMessages(limit = 100, before?: number) {
  const rows = before
    ? db
        .prepare(
          "SELECT * FROM messages WHERE id < ? ORDER BY id DESC LIMIT ?",
        )
        .all(before, limit)
    : db.prepare("SELECT * FROM messages ORDER BY id DESC LIMIT ?").all(limit);
  return rows.map((row) => mapMessage(row as Record<string, unknown>)).reverse();
}

export function markDeliveredFor(recipient: Role) {
  const sender: Role = recipient === "ADMIN" ? "GUEST" : "ADMIN";
  const timestamp = now();
  db.prepare(
    "UPDATE messages SET delivered_at = COALESCE(delivered_at, ?) WHERE sender = ? AND delivered_at IS NULL",
  ).run(timestamp, sender);
  return timestamp;
}

export function markMessageDelivered(messageId: number) {
  const deliveredAt = now();
  db.prepare(
    "UPDATE messages SET delivered_at = COALESCE(delivered_at, ?) WHERE id = ?",
  ).run(deliveredAt, messageId);
  const row = db
    .prepare("SELECT * FROM messages WHERE id = ?")
    .get(messageId) as Record<string, unknown> | undefined;
  return row ? mapMessage(row) : null;
}

export function markMessageRead(messageId: number, reader: Role) {
  const message = db
    .prepare("SELECT sender FROM messages WHERE id = ?")
    .get(messageId) as { sender: Role } | undefined;
  if (!message || message.sender === reader) return null;
  const readAt = now();
  db.prepare(
    "UPDATE messages SET delivered_at = COALESCE(delivered_at, ?), read_at = COALESCE(read_at, ?) WHERE id = ?",
  ).run(readAt, readAt, messageId);
  const updated = db
    .prepare("SELECT * FROM messages WHERE id = ?")
    .get(messageId) as Record<string, unknown> | undefined;
  return updated ? mapMessage(updated) : null;
}

export function recordEvent(type: string, detail: Record<string, unknown> = {}) {
  const createdAt = now();
  const result = db
    .prepare("INSERT INTO events(type, detail, created_at) VALUES (?, ?, ?)")
    .run(type, JSON.stringify(detail), createdAt);
  return { id: Number(result.lastInsertRowid), type, detail, createdAt };
}

export function listEvents(limit = 100) {
  return db
    .prepare("SELECT * FROM events ORDER BY id DESC LIMIT ?")
    .all(limit)
    .map((row) => {
      const event = row as Record<string, unknown>;
      return {
        id: Number(event["id"]),
        type: String(event["type"]),
        detail: parseJson(event["detail"], {}),
        createdAt: String(event["created_at"]),
      };
    });
}

function endOpenSession(table: "connection_sessions" | "activity_sessions", time: string) {
  db.prepare(`UPDATE ${table} SET ended_at = ? WHERE ended_at IS NULL`).run(time);
}

function startSession(table: "connection_sessions" | "activity_sessions", time: string) {
  const existing = db
    .prepare(`SELECT id FROM ${table} WHERE ended_at IS NULL LIMIT 1`)
    .get();
  if (!existing) {
    db.prepare(`INSERT INTO ${table}(started_at) VALUES (?)`).run(time);
  }
}

export function setSharingActive(active: boolean) {
  const timestamp = now();
  const previous = getMonitorSnapshot();
  db.prepare(
    "UPDATE monitor_snapshot SET sharing_active = ?, service_active = CASE WHEN ? = 0 THEN 0 ELSE service_active END, last_update = ? WHERE id = 1",
  ).run(active ? 1 : 0, active ? 1 : 0, timestamp);
  if (previous.sharingActive !== active) {
    if (!active) {
      if (previous.serviceActive) {
        recordEvent("monitoring_service_stopped", { reason: "sharing_revoked" });
      }
      endOpenSession("connection_sessions", timestamp);
      endOpenSession("activity_sessions", timestamp);
    }
    recordEvent(active ? "sharing_activated" : "sharing_revoked", {});
  }
  return getGuestSnapshot();
}

export function updateHeartbeat(input: {
  internetStatus: InternetStatus;
  networkType: string;
  messengerForeground: boolean;
  serviceActive: boolean;
  permissions: Record<string, unknown>;
}) {
  const timestamp = now();
  const previous = getMonitorSnapshot();
  const isFirst = previous.lastUpdate === null;

  if (previous.internetStatus !== input.internetStatus && input.internetStatus !== "unknown") {
    if (input.internetStatus === "connected") {
      startSession("connection_sessions", timestamp);
      recordEvent(
        previous.internetStatus === "disconnected"
          ? "internet_restored"
          : "internet_connected",
        { networkType: input.networkType },
      );
    } else {
      endOpenSession("connection_sessions", timestamp);
      recordEvent("internet_disconnected", {
        previousNetworkType: previous.networkType,
      });
    }
  } else if (isFirst && input.internetStatus === "connected") {
    startSession("connection_sessions", timestamp);
    recordEvent("internet_connected", { networkType: input.networkType });
  }

  if (
    previous.lastUpdate &&
    previous.networkType !== input.networkType &&
    input.internetStatus === "connected"
  ) {
    recordEvent("network_type_changed", {
      from: previous.networkType,
      to: input.networkType,
    });
  }

  if (
    previous.messengerForeground !== null &&
    previous.messengerForeground !== input.messengerForeground
  ) {
    recordEvent(
      input.messengerForeground ? "messenger_opened" : "messenger_backgrounded",
      {},
    );
  } else if (isFirst && input.messengerForeground) {
    recordEvent("messenger_opened", {});
  }
  if (previous.messengerForeground !== input.messengerForeground) {
    if (input.messengerForeground) {
      startSession("activity_sessions", timestamp);
    } else {
      endOpenSession("activity_sessions", timestamp);
    }
  }
  if (previous.serviceActive !== input.serviceActive) {
    recordEvent(
      input.serviceActive ? "monitoring_service_started" : "monitoring_service_stopped",
      {},
    );
  }

  const oldNotifications = previous.permissions["notificationsAllowed"];
  const currentNotifications = input.permissions["notificationsAllowed"];
  if (oldNotifications === true && currentNotifications === false) {
    recordEvent("permissions_revoked", { permission: "notifications" });
  }
  if (
    input.permissions["backgroundRestricted"] === true &&
    previous.permissions["backgroundRestricted"] !== true
  ) {
    recordEvent("battery_restriction_detected", {
      backgroundRestricted: true,
    });
  }

  db.prepare(
    `UPDATE monitor_snapshot
     SET internet_status = ?, network_type = ?, messenger_foreground = ?,
         service_active = ?, permissions = ?, last_update = ?
     WHERE id = 1`,
  ).run(
    input.internetStatus,
    input.networkType,
    input.messengerForeground ? 1 : 0,
    input.serviceActive ? 1 : 0,
    JSON.stringify(input.permissions),
    timestamp,
  );
  return getGuestSnapshot();
}

export function getMonitorSnapshot() {
  const row = db.prepare("SELECT * FROM monitor_snapshot WHERE id = 1").get() as
    | Record<string, unknown>
    | undefined;
  return {
    sharingActive: Boolean(row?.["sharing_active"]),
    internetStatus: (row?.["internet_status"] as InternetStatus) ?? "unknown",
    networkType: String(row?.["network_type"] ?? "unknown"),
    messengerForeground:
      row?.["messenger_foreground"] === null ||
      row?.["messenger_foreground"] === undefined
        ? null
        : Boolean(row["messenger_foreground"]),
    serviceActive: Boolean(row?.["service_active"]),
    permissions: parseJson<Record<string, unknown>>(row?.["permissions"], {}),
    lastUpdate: (row?.["last_update"] as string | null) ?? null,
  };
}

export function getGuestSnapshot(): GuestSnapshot {
  const snapshot = getMonitorSnapshot();
  const ageMs = snapshot.lastUpdate
    ? Date.now() - new Date(snapshot.lastUpdate).getTime()
    : Number.POSITIVE_INFINITY;
  let state: MonitorState = "UNKNOWN";
  if (snapshot.sharingActive && snapshot.lastUpdate) {
    if (ageMs > 45_000) state = "STALE";
    else if (snapshot.internetStatus === "connected") state = "ONLINE";
    else if (snapshot.internetStatus === "disconnected") state = "OFFLINE_CONFIRMED";
  }
  const serverConnectivity =
    !snapshot.sharingActive || !snapshot.lastUpdate
      ? "unknown"
      : ageMs > 45_000
        ? "stale"
        : "connected";
  return { ...snapshot, serverConnectivity, state };
}

export function connectionStatistics(period: "day" | "week") {
  const startMs = Date.now() - (period === "day" ? 86_400_000 : 604_800_000);
  const start = new Date(startMs).toISOString();
  const rows = db
    .prepare(
      `SELECT started_at, ended_at FROM connection_sessions
       WHERE COALESCE(ended_at, started_at) >= ? ORDER BY started_at`,
    )
    .all(start) as Array<{ started_at: string; ended_at: string | null }>;
  const lastUpdate = getMonitorSnapshot().lastUpdate;
  const nowMs = Date.now();
  const intervals = rows
    .map((row) => {
      const begin = Math.max(startMs, Date.parse(row.started_at));
      const inferredEnd = row.ended_at
        ? Date.parse(row.ended_at)
        : lastUpdate
          ? Date.parse(lastUpdate)
          : Date.parse(row.started_at);
      return Math.max(0, inferredEnd - begin);
    })
    .filter((duration) => duration > 0);
  const totalMs = intervals.reduce((sum, duration) => sum + duration, 0);
  const completedRows = rows.filter((row) => row.ended_at !== null);
  const completedMs = completedRows.reduce((sum, row) => {
    const begin = Math.max(startMs, Date.parse(row.started_at));
    const end = Date.parse(row.ended_at as string);
    return sum + Math.max(0, end - begin);
  }, 0);
  const interruptionStart = start;
  const interruptions = Number(
    (
      db
        .prepare(
          "SELECT COUNT(*) AS count FROM events WHERE type = 'internet_disconnected' AND created_at >= ?",
        )
        .get(interruptionStart) as { count: number }
    ).count,
  );
  return {
    totalSeconds: Math.floor(totalMs / 1000),
    interruptions,
    averageSessionSeconds:
      completedRows.length > 0
        ? Math.floor(completedMs / 1000 / completedRows.length)
        : 0,
    longestSessionSeconds: Math.floor(Math.max(0, ...intervals) / 1000),
    activeSession: getGuestSnapshot().state === "ONLINE",
    asOf: lastUpdate,
    observedAt: new Date(nowMs).toISOString(),
  };
}

export function activityHistory(limit = 100) {
  return db
    .prepare(
      "SELECT id, started_at, ended_at FROM activity_sessions ORDER BY id DESC LIMIT ?",
    )
    .all(limit)
    .map((row) => {
      const session = row as {
        id: number;
        started_at: string;
        ended_at: string | null;
      };
      return {
        id: session.id,
        startedAt: session.started_at,
        endedAt: session.ended_at,
      };
    });
}

export function connectionHistory(limit = 100) {
  const lastUpdate = getMonitorSnapshot().lastUpdate;
  return db
    .prepare(
      "SELECT id, started_at, ended_at FROM connection_sessions ORDER BY id DESC LIMIT ?",
    )
    .all(limit)
    .map((row) => {
      const session = row as {
        id: number;
        started_at: string;
        ended_at: string | null;
      };
      const observedEnd = session.ended_at ?? lastUpdate;
      const durationSeconds =
        observedEnd === null
          ? null
          : Math.max(
              0,
              Math.floor(
                (Date.parse(observedEnd) - Date.parse(session.started_at)) / 1000,
              ),
            );
      return {
        id: session.id,
        startedAt: session.started_at,
        endedAt: session.ended_at,
        durationSeconds,
        ongoing: session.ended_at === null,
      };
    });
}
