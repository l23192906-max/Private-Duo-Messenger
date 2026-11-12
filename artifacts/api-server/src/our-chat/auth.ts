import type { NextFunction, Request, Response } from "express";
import { roleForToken, type Role } from "./store";

export type AuthenticatedRequest = Request & { chatRole: Role };

export function authenticateRequest(
  req: Request,
  res: Response,
  next: NextFunction,
) {
  const authorization = req.header("authorization");
  const token = authorization?.startsWith("Bearer ")
    ? authorization.slice(7).trim()
    : "";
  const role = token ? roleForToken(token) : null;
  if (!role) {
    res.status(401).json({ error: "Sign in with one of the configured usernames." });
    return;
  }
  (req as AuthenticatedRequest).chatRole = role;
  next();
}

export function requireAdmin(
  req: Request,
  res: Response,
  next: NextFunction,
) {
  if ((req as AuthenticatedRequest).chatRole !== "ADMIN") {
    res.status(403).json({ error: "This action is available to ADMIN only." });
    return;
  }
  next();
}
