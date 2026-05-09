import { Router, Response } from 'express';
import { query } from '../db';
import { requireAuth, AuthenticatedRequest } from '../auth';

const router = Router();
router.use(requireAuth);

// GET /api/profiles — list all profiles
router.get('/', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  try {
    const q = req.query as Record<string, string>;
    const params: any[] = [];
    let idx = 1;
    const conditions: string[] = ['1=1'];

    if (q.id_eq) { conditions.push(`id = $${idx++}`); params.push(q.id_eq); }
    if (q.is_admin_eq) { conditions.push(`is_admin = $${idx++}`); params.push(q.is_admin_eq === 'true'); }

    const selectCols = q.select
      ? q.select.split(',').map((c) => c.trim().replace(/[^a-z_]/g, '')).join(', ')
      : '*';

    const { rows } = await query(
      `SELECT ${selectCols} FROM profiles WHERE ${conditions.join(' AND ')} ORDER BY created_at`,
      params
    );

    if (q.single === '1') {
      if (rows.length === 0) {
        res.status(406).json({ message: 'Not found', code: 'PGRST116' });
        return;
      }
      res.json(rows[0]);
      return;
    }

    res.json(rows);
  } catch (err: any) {
    console.error('GET /profiles error:', err.message);
    res.status(500).json({ message: err.message });
  }
});

// GET /api/profiles/:id
router.get('/:id', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  try {
    const { rows } = await query('SELECT * FROM profiles WHERE id = $1', [req.params.id]);
    if (rows.length === 0) {
      res.status(404).json({ message: 'Profile not found' });
      return;
    }
    res.json(rows[0]);
  } catch (err: any) {
    res.status(500).json({ message: err.message });
  }
});

// PUT /api/profiles/:id — update profile
router.put('/:id', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const { display_name, is_admin } = req.body;
  const updates: string[] = [];
  const params: any[] = [];
  let idx = 1;

  if (display_name !== undefined) { updates.push(`display_name = $${idx++}`); params.push(display_name); }
  if (is_admin !== undefined) { updates.push(`is_admin = $${idx++}`); params.push(is_admin); }

  if (updates.length === 0) {
    res.status(400).json({ message: 'No fields to update' });
    return;
  }

  params.push(req.params.id);
  try {
    const { rows } = await query(
      `UPDATE profiles SET ${updates.join(', ')} WHERE id = $${idx} RETURNING *`,
      params
    );
    if (rows.length === 0) {
      res.status(404).json({ message: 'Profile not found' });
      return;
    }
    res.json(rows[0]);
  } catch (err: any) {
    res.status(500).json({ message: err.message });
  }
});

// POST /api/profiles (upsert — for profile creation fallback)
router.post('/', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const { id, display_name } = req.body;

  const profileId = id || req.user!.id;

  try {
    const { rows } = await query(
      `INSERT INTO profiles (id, display_name)
       VALUES ($1, $2)
       ON CONFLICT (id) DO UPDATE
         SET display_name = EXCLUDED.display_name
       RETURNING *`,
      [profileId, display_name || 'Użytkownik']
    );
    res.status(201).json(rows[0]);
  } catch (err: any) {
    res.status(500).json({ message: err.message });
  }
});

export default router;
