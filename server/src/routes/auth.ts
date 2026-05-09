import { Router, Request, Response } from 'express';
import bcrypt from 'bcrypt';
import { query } from '../db';
import {
  signAccessToken,
  signRefreshToken,
  verifyRefreshToken,
  buildSession,
  buildUserObject,
  requireAuth,
  AuthenticatedRequest,
} from '../auth';

const router = Router();

// POST /api/auth/register
router.post('/register', async (req: Request, res: Response): Promise<void> => {
  const { email, password, display_name } = req.body;

  if (!email || !password || !display_name) {
    res.status(400).json({ message: 'email, password, display_name are required' });
    return;
  }

  if (password.length < 6) {
    res.status(400).json({ message: 'Password must be at least 6 characters' });
    return;
  }

  try {
    const { rows: existing } = await query('SELECT id FROM users WHERE email = $1', [email]);
    if (existing.length > 0) {
      res.status(409).json({ message: 'User already registered' });
      return;
    }

    const passwordHash = await bcrypt.hash(password, 10);

    const { rows: [user] } = await query(
      'INSERT INTO users (email, password_hash) VALUES ($1, $2) RETURNING id, email',
      [email.toLowerCase().trim(), passwordHash]
    );

    await query(
      'INSERT INTO profiles (id, display_name) VALUES ($1, $2)',
      [user.id, display_name]
    );

    const accessToken = signAccessToken(user.id, user.email, display_name);
    const refreshToken = signRefreshToken(user.id, user.email, display_name);

    const session = buildSession(accessToken, refreshToken, user.id, user.email, display_name);
    res.status(201).json({ session, user: session.user });
  } catch (err: any) {
    console.error('Register error:', err.message);
    res.status(500).json({ message: 'Internal server error' });
  }
});

// POST /api/auth/login
router.post('/login', async (req: Request, res: Response): Promise<void> => {
  const { email, password } = req.body;

  if (!email || !password) {
    res.status(400).json({ message: 'email and password are required' });
    return;
  }

  try {
    const { rows } = await query(
      `SELECT u.id, u.email, u.password_hash, p.display_name
       FROM users u
       LEFT JOIN profiles p ON p.id = u.id
       WHERE u.email = $1`,
      [email.toLowerCase().trim()]
    );

    if (rows.length === 0) {
      res.status(401).json({ message: 'Invalid login credentials' });
      return;
    }

    const user = rows[0];
    const passwordMatch = await bcrypt.compare(password, user.password_hash);
    if (!passwordMatch) {
      res.status(401).json({ message: 'Invalid login credentials' });
      return;
    }

    const displayName = user.display_name || 'Użytkownik';
    const accessToken = signAccessToken(user.id, user.email, displayName);
    const refreshToken = signRefreshToken(user.id, user.email, displayName);

    const session = buildSession(accessToken, refreshToken, user.id, user.email, displayName);
    res.json({ session, user: session.user });
  } catch (err: any) {
    console.error('Login error:', err.message);
    res.status(500).json({ message: 'Internal server error' });
  }
});

// POST /api/auth/refresh
router.post('/refresh', async (req: Request, res: Response): Promise<void> => {
  const { refresh_token } = req.body;

  if (!refresh_token) {
    res.status(400).json({ message: 'refresh_token is required' });
    return;
  }

  try {
    const payload = verifyRefreshToken(refresh_token);
    if (payload.type !== 'refresh') {
      res.status(401).json({ message: 'Invalid token type' });
      return;
    }

    const { rows } = await query(
      `SELECT u.id, u.email, p.display_name
       FROM users u
       LEFT JOIN profiles p ON p.id = u.id
       WHERE u.id = $1`,
      [payload.sub]
    );

    if (rows.length === 0) {
      res.status(401).json({ message: 'User not found' });
      return;
    }

    const user = rows[0];
    const displayName = user.display_name || 'Użytkownik';
    const newAccessToken = signAccessToken(user.id, user.email, displayName);
    const newRefreshToken = signRefreshToken(user.id, user.email, displayName);

    const session = buildSession(newAccessToken, newRefreshToken, user.id, user.email, displayName);
    res.json({ session });
  } catch {
    res.status(401).json({ message: 'Invalid or expired refresh token' });
  }
});

// GET /api/auth/me
router.get('/me', requireAuth, async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  try {
    const { rows } = await query(
      `SELECT u.id, u.email, p.display_name, p.is_admin
       FROM users u
       LEFT JOIN profiles p ON p.id = u.id
       WHERE u.id = $1`,
      [req.user!.id]
    );

    if (rows.length === 0) {
      res.status(404).json({ message: 'User not found' });
      return;
    }

    const user = rows[0];
    res.json(buildUserObject(user.id, user.email, user.display_name || 'Użytkownik'));
  } catch (err: any) {
    console.error('Me error:', err.message);
    res.status(500).json({ message: 'Internal server error' });
  }
});

export default router;
