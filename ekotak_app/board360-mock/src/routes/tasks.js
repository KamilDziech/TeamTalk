'use strict';
/* Zadania zespolu (kontrakt board360 FR-26) i lista czlonkow do przypisania. */

const express = require('express');
const { uuid, nowIso } = require('../crypto');
const { requireAuth, requirePermission, unprocessable } = require('../middleware');
const { db, userById } = require('../store');

const router = express.Router();

const TASK_PRIORITIES = new Set(['low', 'normal', 'high']);

router.get('/tasks/members', requireAuth, requirePermission('tasks.view'), (req, res) => {
  const list = db.users
    .filter((u) => u.organizationId === req.user.organizationId)
    .map((u) => ({
      id: u.id,
      email: u.email,
      firstName: u.firstName || null,
      lastName: u.lastName || null,
      role: u.role,
    }));
  res.json(list);
});

router.post('/tasks', requireAuth, requirePermission('tasks.manage'), (req, res) => {
  const b = req.body || {};
  const title = typeof b.title === 'string' ? b.title.trim() : '';
  if (!title) return unprocessable(res, 'Tytul jest wymagany.', ['tytul zadania']);

  let assigneeId = null;
  let assigneeEmail = null;
  if (b.assigneeId) {
    const member = userById(req.user.organizationId, b.assigneeId);
    if (!member) return unprocessable(res, 'Nieznany pracownik (assigneeId).');
    assigneeId = member.id;
    assigneeEmail = member.email;
  }

  const row = {
    id: uuid(),
    organizationId: req.user.organizationId,
    dealId: b.dealId || null,
    title,
    description: b.description ? String(b.description) : null,
    assigneeId,
    assigneeEmail,
    dueAt: b.dueAt ? new Date(b.dueAt).toISOString() : null,
    status: 'open',
    priority: TASK_PRIORITIES.has(b.priority) ? b.priority : 'normal',
    section: null,
    estimatedMinutes: null,
    createdBy: req.user.id,
    createdAt: nowIso(),
    updatedAt: nowIso(),
  };
  db.tasks.push(row);
  return res.status(201).json(row);
});

router.get('/tasks', requireAuth, requirePermission('tasks.view'), (req, res) => {
  const statusF = req.query.status ? String(req.query.status) : null;
  const assigneeF = req.query.assignee === 'me' ? req.user.id : req.query.assignee || null;

  let list = db.tasks.filter((t) => t.organizationId === req.user.organizationId);
  if (statusF) list = list.filter((t) => t.status === statusF);
  if (assigneeF) list = list.filter((t) => t.assigneeId === assigneeF);
  list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  res.json(list);
});

module.exports = router;
