import jwt from 'jsonwebtoken';
import { Request, Response, NextFunction } from 'express';
import { query } from './db';

const ACCESS_SECRET = process.env.JWT_ACCESS_SECRET || 'dev_access_secret';
const REFRESH_SECRET = process.env.JWT_REFRESH_SECRET || 'dev_refresh_secret';
const ACCESS_EXPIRES = process.env.JWT_ACCESS_EXPIRES_IN || '1h';
const REFRESH_EXPIRES = process.env.JWT_REFRESH_EXPIRES_IN || '30d';

export interface TokenPayload {
  sub: string;
  email: string;
  display_name: string;
  type: 'access' | 'refresh';
}

export function signAccessToken(userId: string, email: string, displayName: string): string {
  return jwt.sign(
    { sub: userId, email, display_name: displayName, type: 'access' },
    ACCESS_SECRET,
    { expiresIn: ACCESS_EXPIRES } as jwt.SignOptions
  );
}

export function signRefreshToken(userId: string, email: string, displayName: string): string {
  return jwt.sign(
    { sub: userId, email, display_name: displayName, type: 'refresh' },
    REFRESH_SECRET,
    { expiresIn: REFRESH_EXPIRES } as jwt.SignOptions
  );
}

export function verifyAccessToken(token: string): TokenPayload {
  return jwt.verify(token, ACCESS_SECRET) as TokenPayload;
}

export function verifyRefreshToken(token: string): TokenPayload {
  return jwt.verify(token, REFRESH_SECRET) as TokenPayload;
}

export function decodeAccessExpiry(token: string): number {
  const payload = jwt.decode(token) as { exp?: number };
  return payload?.exp ?? 0;
}

export interface AuthenticatedRequest extends Request {
  user?: { id: string; email: string; display_name: string };
}

export function requireAuth(
  req: AuthenticatedRequest,
  res: Response,
  next: NextFunction
): void {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    res.status(401).json({ message: 'Unauthorized' });
    return;
  }

  const token = authHeader.slice(7);
  try {
    const payload = verifyAccessToken(token);
    if (payload.type !== 'access') {
      res.status(401).json({ message: 'Invalid token type' });
      return;
    }
    req.user = {
      id: payload.sub,
      email: payload.email,
      display_name: payload.display_name,
    };
    next();
  } catch {
    res.status(401).json({ message: 'Invalid or expired token' });
  }
}

export function buildUserObject(userId: string, email: string, displayName: string) {
  return {
    id: userId,
    email,
    user_metadata: { display_name: displayName },
    app_metadata: {},
  };
}

export function buildSession(
  accessToken: string,
  refreshToken: string,
  userId: string,
  email: string,
  displayName: string
) {
  const decoded = jwt.decode(accessToken) as { exp?: number };
  const expiresAt = decoded?.exp ?? 0;
  return {
    access_token: accessToken,
    refresh_token: refreshToken,
    token_type: 'bearer',
    expires_at: expiresAt,
    expires_in: Math.max(0, expiresAt - Math.floor(Date.now() / 1000)),
    user: buildUserObject(userId, email, displayName),
  };
}
