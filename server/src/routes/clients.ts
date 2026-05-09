import { Router, Response } from 'express';
import { query } from '../db';
import { requireAuth, AuthenticatedRequest } from '../auth';

const router = Router();
router.use(requireAuth);

// GET /api/clients
router.get('/', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  try {
    const { phone_eq, id_in, order = 'created_at.desc', limit, single } = req.query as Record<string, string>;

    let sql = 'SELECT * FROM clients WHERE 1=1';
    const params: any[] = [];
    let idx = 1;

    if (phone_eq) {
      sql += ` AND phone = $${idx++}`;
      params.push(phone_eq);
    }

    if (id_in) {
      const ids = id_in.split(',').filter(Boolean);
      if (ids.length > 0) {
        sql += ` AND id = ANY($${idx++}::uuid[])`;
        params.push(ids);
      } else {
        res.json([]);
        return;
      }
    }

    if (order) {
      const [col, dir] = order.split('.');
      const safeCol = col.replace(/[^a-z_]/g, '');
      const safeDir = dir === 'asc' ? 'ASC' : 'DESC';
      sql += ` ORDER BY ${safeCol} ${safeDir}`;
    }

    if (limit) {
      sql += ` LIMIT $${idx++}`;
      params.push(parseInt(limit));
    }

    const { rows } = await query(sql, params);

    if (single === '1') {
      if (rows.length === 0) {
        res.status(406).json({ message: 'Not found', code: 'PGRST116' });
        return;
      }
      res.json(rows[0]);
      return;
    }

    res.json(rows);
  } catch (err: any) {
    console.error('GET /clients error:', err.message);
    res.status(500).json({ message: err.message });
  }
});

// GET /api/clients/:id
router.get('/:id', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  try {
    const { rows } = await query('SELECT * FROM clients WHERE id = $1', [req.params.id]);
    if (rows.length === 0) {
      res.status(404).json({ message: 'Client not found' });
      return;
    }
    res.json(rows[0]);
  } catch (err: any) {
    res.status(500).json({ message: err.message });
  }
});

// POST /api/clients
router.post('/', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const { phone, name, address, notes } = req.body;

  if (!phone) {
    res.status(400).json({ message: 'phone is required' });
    return;
  }

  try {
    const { rows } = await query(
      `INSERT INTO clients (phone, name, address, notes)
       VALUES ($1, $2, $3, $4)
       RETURNING *`,
      [phone, name || null, address || null, notes || null]
    );
    res.status(201).json(rows[0]);
  } catch (err: any) {
    if (err.code === '23505') {
      res.status(409).json({ message: 'Phone number already exists', code: '23505' });
      return;
    }
    console.error('POST /clients error:', err.message);
    res.status(500).json({ message: err.message });
  }
});

// PUT /api/clients/:id
router.put('/:id', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const { address, notes, name, phone } = req.body;
  const updates: string[] = [];
  const params: any[] = [];
  let idx = 1;

  if (address !== undefined) { updates.push(`address = $${idx++}`); params.push(address); }
  if (notes !== undefined) { updates.push(`notes = $${idx++}`); params.push(notes); }
  if (name !== undefined) { updates.push(`name = $${idx++}`); params.push(name); }
  if (phone !== undefined) { updates.push(`phone = $${idx++}`); params.push(phone); }

  if (updates.length === 0) {
    res.status(400).json({ message: 'No fields to update' });
    return;
  }

  params.push(req.params.id);

  try {
    const { rows } = await query(
      `UPDATE clients SET ${updates.join(', ')} WHERE id = $${idx} RETURNING *`,
      params
    );
    if (rows.length === 0) {
      res.status(404).json({ message: 'Client not found' });
      return;
    }
    res.json(rows[0]);
  } catch (err: any) {
    res.status(500).json({ message: err.message });
  }
});

// DELETE /api/clients/:id
router.delete('/:id', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  try {
    await query('DELETE FROM clients WHERE id = $1', [req.params.id]);
    res.status(204).send();
  } catch (err: any) {
    res.status(500).json({ message: err.message });
  }
});

export default router;
