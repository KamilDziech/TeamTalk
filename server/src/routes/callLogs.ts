import { Router, Response } from 'express';
import { query } from '../db';
import { requireAuth, AuthenticatedRequest } from '../auth';

const router = Router();
router.use(requireAuth);

// GET /api/call-logs
router.get('/', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  try {
    const q = req.query as Record<string, string>;
    const params: any[] = [];
    let idx = 1;
    const conditions: string[] = ['1=1'];

    if (q.status_eq) { conditions.push(`cl.status = $${idx++}`); params.push(q.status_eq); }
    if (q.status_neq) { conditions.push(`cl.status != $${idx++}`); params.push(q.status_neq); }
    if (q.type_neq) { conditions.push(`cl.type != $${idx++}`); params.push(q.type_neq); }
    if (q.type_neq2) { conditions.push(`cl.type != $${idx++}`); params.push(q.type_neq2); }
    if (q.client_id_eq) { conditions.push(`cl.client_id = $${idx++}`); params.push(q.client_id_eq); }
    if (q.caller_phone_eq) { conditions.push(`cl.caller_phone = $${idx++}`); params.push(q.caller_phone_eq); }
    if (q.id_neq) { conditions.push(`cl.id != $${idx++}`); params.push(q.id_neq); }
    if (q.client_id_not_is_null) { conditions.push(`cl.client_id IS NOT NULL`); }

    if (q.status_in) {
      const vals = q.status_in.split(',').filter(Boolean);
      conditions.push(`cl.status = ANY($${idx++})`);
      params.push(vals);
    }

    if (q.id_in) {
      const ids = q.id_in.split(',').filter(Boolean);
      if (ids.length > 0) {
        conditions.push(`cl.id = ANY($${idx++}::uuid[])`);
        params.push(ids);
      } else {
        res.json([]);
        return;
      }
    }

    if (q.timestamp_gte) { conditions.push(`cl.timestamp >= $${idx++}`); params.push(q.timestamp_gte); }
    if (q.timestamp_lte) { conditions.push(`cl.timestamp <= $${idx++}`); params.push(q.timestamp_lte); }

    const embedClient = q.embed === 'clients' || (q.select || '').includes('clients');

    let sql: string;
    if (embedClient) {
      sql = `
        SELECT cl.*,
               row_to_json(c.*) AS clients
        FROM call_logs cl
        LEFT JOIN clients c ON c.id = cl.client_id
        WHERE ${conditions.join(' AND ')}
      `;
    } else {
      sql = `SELECT cl.* FROM call_logs cl WHERE ${conditions.join(' AND ')}`;
    }

    const order = q.order || 'timestamp.desc';
    const [col, dir] = order.split('.');
    const safeCol = col.replace(/[^a-z_]/g, '');
    const safeDir = dir === 'asc' ? 'ASC' : 'DESC';
    sql += ` ORDER BY cl.${safeCol} ${safeDir}`;

    if (q.limit) {
      sql += ` LIMIT $${idx++}`;
      params.push(parseInt(q.limit));
    }

    const { rows } = await query(sql, params);

    // If embed=clients, rename 'clients' key to match Supabase response
    // Supabase returns it as clients: {...} when doing select('*, clients (*)')
    if (embedClient) {
      const mapped = rows.map((r: any) => {
        const { clients, ...rest } = r;
        return { ...rest, clients, client: clients };
      });
      res.json(mapped);
      return;
    }

    res.json(rows);
  } catch (err: any) {
    console.error('GET /call-logs error:', err.message);
    res.status(500).json({ message: err.message });
  }
});

// POST /api/call-logs
router.post('/', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const {
    client_id, employee_id, type, status, timestamp,
    reservation_by, reservation_at, recipients, caller_phone,
    dedup_key, phone_account_id,
  } = req.body;

  try {
    const { rows } = await query(
      `INSERT INTO call_logs
         (client_id, employee_id, type, status, timestamp,
          reservation_by, reservation_at, recipients, caller_phone,
          dedup_key, phone_account_id)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)
       RETURNING *`,
      [
        client_id || null,
        employee_id || null,
        type,
        status,
        timestamp || new Date().toISOString(),
        reservation_by || null,
        reservation_at || null,
        recipients || [],
        caller_phone || null,
        dedup_key || null,
        phone_account_id || null,
      ]
    );
    res.status(201).json(rows[0]);
  } catch (err: any) {
    if (err.code === '23505') {
      res.status(409).json({ message: 'Duplicate call log', code: '23505' });
      return;
    }
    console.error('POST /call-logs error:', err.message);
    res.status(500).json({ message: err.message });
  }
});

// PUT /api/call-logs/:id
router.put('/:id', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const allowed = ['status', 'type', 'reservation_by', 'reservation_at', 'merged_into_id'];
  const updates: string[] = [];
  const params: any[] = [];
  let idx = 1;

  for (const field of allowed) {
    if (req.body[field] !== undefined) {
      updates.push(`${field} = $${idx++}`);
      params.push(req.body[field]);
    }
  }

  if (updates.length === 0) {
    res.status(400).json({ message: 'No valid fields to update' });
    return;
  }

  params.push(req.params.id);
  try {
    const { rows } = await query(
      `UPDATE call_logs SET ${updates.join(', ')} WHERE id = $${idx} RETURNING *`,
      params
    );
    if (rows.length === 0) {
      res.status(404).json({ message: 'Call log not found' });
      return;
    }
    res.json(rows[0]);
  } catch (err: any) {
    res.status(500).json({ message: err.message });
  }
});

// PUT /api/call-logs (bulk update with filters)
router.put('/', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const q = req.query as Record<string, string>;
  const { body } = req;

  const allowed = ['status', 'type', 'reservation_by', 'reservation_at', 'merged_into_id'];
  const updates: string[] = [];
  const params: any[] = [];
  let idx = 1;

  for (const field of allowed) {
    if (body[field] !== undefined) {
      updates.push(`${field} = $${idx++}`);
      params.push(body[field]);
    }
  }

  if (updates.length === 0) {
    res.status(400).json({ message: 'No valid fields to update' });
    return;
  }

  const conditions: string[] = ['1=1'];
  if (q.status_eq) { conditions.push(`status = $${idx++}`); params.push(q.status_eq); }
  if (q.caller_phone_eq) { conditions.push(`caller_phone = $${idx++}`); params.push(q.caller_phone_eq); }
  if (q.client_id_eq) { conditions.push(`client_id = $${idx++}`); params.push(q.client_id_eq); }
  if (q.type_neq) { conditions.push(`type != $${idx++}`); params.push(q.type_neq); }
  if (q.type_neq2) { conditions.push(`type != $${idx++}`); params.push(q.type_neq2); }
  if (q.id_neq) { conditions.push(`id != $${idx++}`); params.push(q.id_neq); }

  if (q.id_in) {
    const ids = q.id_in.split(',').filter(Boolean);
    if (ids.length > 0) {
      conditions.push(`id = ANY($${idx++}::uuid[])`);
      params.push(ids);
    }
  }

  try {
    const { rows } = await query(
      `UPDATE call_logs SET ${updates.join(', ')} WHERE ${conditions.join(' AND ')} RETURNING *`,
      params
    );
    res.json(rows);
  } catch (err: any) {
    res.status(500).json({ message: err.message });
  }
});

// POST /api/rpc/append-unique-recipient
router.post('/rpc/append-unique-recipient', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const { p_call_log_id, p_recipient_id } = req.body;

  if (!p_call_log_id || !p_recipient_id) {
    res.status(400).json({ message: 'p_call_log_id and p_recipient_id are required' });
    return;
  }

  try {
    await query('SELECT append_unique_recipient($1, $2)', [p_call_log_id, p_recipient_id]);
    res.status(204).send();
  } catch (err: any) {
    res.status(500).json({ message: err.message });
  }
});

export default router;
