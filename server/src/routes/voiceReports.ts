import { Router, Response } from 'express';
import { query } from '../db';
import { requireAuth, AuthenticatedRequest } from '../auth';

const router = Router();
router.use(requireAuth);

// GET /api/voice-reports
router.get('/', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  try {
    const q = req.query as Record<string, string>;
    const params: any[] = [];
    let idx = 1;
    const conditions: string[] = ['1=1'];

    if (q.call_log_id_in) {
      const ids = q.call_log_id_in.split(',').filter(Boolean);
      if (ids.length > 0) {
        conditions.push(`call_log_id = ANY($${idx++}::uuid[])`);
        params.push(ids);
      } else {
        res.json([]);
        return;
      }
    }

    if (q.call_log_id_eq) {
      conditions.push(`call_log_id = $${idx++}`);
      params.push(q.call_log_id_eq);
    }

    let sql = `SELECT * FROM voice_reports WHERE ${conditions.join(' AND ')} ORDER BY created_at DESC`;

    if (q.select) {
      const cols = q.select.split(',').map((c) => c.trim()).filter(Boolean);
      const safeCols = cols.map((c) => c.replace(/[^a-z_]/g, '')).join(', ');
      sql = `SELECT ${safeCols} FROM voice_reports WHERE ${conditions.join(' AND ')} ORDER BY created_at DESC`;
    }

    const { rows } = await query(sql, params);
    res.json(rows);
  } catch (err: any) {
    console.error('GET /voice-reports error:', err.message);
    res.status(500).json({ message: err.message });
  }
});

// POST /api/voice-reports
router.post('/', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const { call_log_id, audio_url, transcription, ai_summary, call_count } = req.body;

  if (!call_log_id) {
    res.status(400).json({ message: 'call_log_id is required' });
    return;
  }

  try {
    const { rows } = await query(
      `INSERT INTO voice_reports
         (call_log_id, audio_url, transcription, ai_summary, created_by, call_count)
       VALUES ($1, $2, $3, $4, $5, $6)
       RETURNING *`,
      [
        call_log_id,
        audio_url || null,
        transcription || null,
        ai_summary || null,
        req.user!.id,
        call_count ?? 1,
      ]
    );
    res.status(201).json(rows[0]);
  } catch (err: any) {
    console.error('POST /voice-reports error:', err.message);
    res.status(500).json({ message: err.message });
  }
});

export default router;
