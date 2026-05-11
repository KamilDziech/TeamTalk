import { Router, Response } from 'express';
import { query } from '../db';
import { requireAuth, AuthenticatedRequest } from '../auth';

const router = Router();
router.use(requireAuth);

// GET /api/client-groups
router.get('/', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  try {
    const { rows } = await query('SELECT * FROM client_groups ORDER BY is_default DESC, name ASC');
    res.json(rows);
  } catch (err: any) {
    res.status(500).json({ message: err.message });
  }
});

// POST /api/client-groups
router.post('/', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  const { name, is_default = false } = req.body;
  if (!name) {
    res.status(400).json({ message: 'name is required' });
    return;
  }
  try {
    const { rows } = await query(
      'INSERT INTO client_groups (name, is_default) VALUES ($1, $2) RETURNING *',
      [name, is_default]
    );
    res.status(201).json(rows[0]);
  } catch (err: any) {
    res.status(500).json({ message: err.message });
  }
});

// DELETE /api/client-groups/:id
router.delete('/:id', async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  try {
    await query('DELETE FROM client_groups WHERE id = $1', [req.params.id]);
    res.status(204).send();
  } catch (err: any) {
    res.status(500).json({ message: err.message });
  }
});

export default router;
