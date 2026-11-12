import type { Server as HttpServer } from "node:http";
import { Server, type Socket } from "socket.io";
import { roleForToken, type Role } from "./store";

let io: Server | null = null;
const connectedUsers: Record<Role, Set<string>> = {
  ADMIN: new Set(),
  GUEST: new Set(),
};

export function attachRealtime(server: HttpServer) {
  io = new Server(server, {
    path: "/api/socket.io",
    cors: { origin: true, methods: ["GET", "POST"] },
  });

  io.use((socket, next) => {
    const token = socket.handshake.auth["token"];
    const role =
      typeof token === "string" && token.length > 0
        ? roleForToken(token)
        : null;
    if (!role) {
      next(new Error("Unauthorized"));
      return;
    }
    socket.data["role"] = role;
    next();
  });

  io.on("connection", (socket: Socket) => {
    const role = socket.data["role"] as Role;
    connectedUsers[role].add(socket.id);
    socket.join(`role:${role}`);
    emitPresence();

    socket.on("disconnect", () => {
      connectedUsers[role].delete(socket.id);
      emitPresence();
    });
  });
}

export function emitToRole(role: Role, event: string, payload: unknown) {
  io?.to(`role:${role}`).emit(event, payload);
}

export function emitToOtherRole(role: Role, event: string, payload: unknown) {
  emitToRole(role === "ADMIN" ? "GUEST" : "ADMIN", event, payload);
}

export function isRoleOnline(role: Role) {
  return connectedUsers[role].size > 0;
}

function emitPresence() {
  const presence = {
    ADMIN: isRoleOnline("ADMIN"),
    GUEST: isRoleOnline("GUEST"),
  };
  io?.emit("presence", presence);
}
