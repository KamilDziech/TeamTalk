import { Router, Response } from 'express';
import { query } from '../db';
import { requireAuth, AuthenticatedRequest } from '../auth';

const router = Router();
router.use(requireAuth);

// GET /api/devices
router.get('/', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  try {
    const q = req.query as Record<string, string>;
    const params: any[] = [];
    let idx = 1;
    const conditions: string[] = ['1=1'];

    if (q.push_token_neq) {
      conditions.push(`push_token != $${idx++}`);
      params.push(q.push_token_neq);
    }

    const selectCols = q.select
      ? q.select.split(',').map((c) => c.trim().replace(/[^a-z_]/g, '')).join(', ')
      : '*';

    const { rows } = await query(
      `SELECT ${selectCols} FROM devices WHERE ${conditions.join(' AND ')} ORDER BY last_active_at DESC`,
      params
    );
    res.json(rows);
  } catch (err: any) {
    res.status(500).json({ message: err.message });
  }
});

// POST /api/devices (upsert by push_token)
router.post('/', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const { push_token, user_name, device_info } = req.body;

  if (!push_token || !user_name) {
    res.status(400).json({ message: 'push_token and user_name are required' });
    return;
  }

  try {
    const { rows } = await query(
      `INSERT INTO devices (push_token, user_name, device_info, last_active_at)
       VALUES ($1, $2, $3, NOW())
       ON CONFLICT (push_token) DO UPDATE
         SET user_name = EXCLUDED.user_name,
             device_info = EXCLUDED.device_info,
             last_active_at = NOW()
       RETURNING *`,
      [push_token, user_name, device_info || null]
    );
    res.status(201).json(rows[0]);
  } catch (err: any) {
    res.status(500).json({ message: err.message });
  }
});

// PUT /api/devices/last-active
router.put('/last-active', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const { push_token } = req.body;

  if (!push_token) {
    res.status(400).json({ message: 'push_token is required' });
    return;
  }

  try {
    await query(
      'UPDATE devices SET last_active_at = NOW() WHERE push_token = $1',
      [push_token]
    );
    res.status(204).send();
  } catch (err: any) {
    res.status(500).json({ message: err.message });
  }
});

export default router;
