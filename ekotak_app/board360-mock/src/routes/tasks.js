'use strict';
/*
 * Zadania zespolu (kontrakt board360 FR-26), lista czlonkow do przypisania
 * i projekty potrzebne krokowi "kogo dotyczy" w kreatorze zadania.
 *
 * Ksztalt rekordu = `api/src/modules/tasks/domain/task.ts` z board360. Pola
 * `dealName` / `projectName` sa doklejane przy odczycie (tak samo jak w panelu,
 * gdzie nie sa kolumnami tabeli) — sluza kolumnie "Zrodla" i wierszowi listy.
 *
 * Sa tu takze komentarze karty zadania z wywolaniami (@) — Komunikator siedzi
 * osobno w `routes/discussions.js`, bo w board360 to inny modul. Czego (jeszcze)
 * nie ma: zalacznikow — wchodza z etapem E5, patrz design/mockups/modul-zadania.html.
 */

const express = require('express');
const { uuid, nowIso } = require('../crypto');
const { requireAuth, requirePermission, unprocessable } = require('../middleware');
const {
  db,
  userById,
  dealById,
  clientById,
  recordMentions,
  markDiscussionRead,
} = require('../store');

const router = express.Router();

const TASK_PRIORITIES = new Set(['low', 'normal', 'high']);
const TASK_STATUSES = new Set(['open', 'in_progress', 'done']);
/** Sekcje = etapy lejka + "dotacja"; kolejnosc jak w `TASK_SECTIONS` board360. */
const TASK_SECTIONS = new Set([
  'audyt',
  'oferta',
  'wstrzymane',
  'sprzedane',
  'przed_montazem',
  'oczekiwanie',
  'montaz',
  'po_montazu',
  'dotacja',
]);
/** SLA liczone od utworzenia: 24 h / 7 dni / 30 dni. Inne wartosci → 422. */
const SLA_HOURS = new Set([24, 168, 720]);

/** Etap deala → sekcja zadania (czesc "auto" hybrydy z board360). */
const SECTION_BY_STAGE = {
  audit: 'audyt',
  angebot: 'oferta',
  on_hold: 'wstrzymane',
  sold: 'sprzedane',
  przed_montazem: 'przed_montazem',
  oczekiwanie_na_montaz: 'oczekiwanie',
  montaz: 'montaz',
  fertig: 'po_montazu',
};

/** Nazwa klienta z deala — do wiersza listy ("kogo dotyczy zadanie"). */
function dealLabel(orgId, dealId) {
  const deal = dealById(orgId, dealId);
  if (!deal) return null;
  const client = clientById(orgId, deal.clientId);
  if (!client) return deal.projectName || null;
  return `${client.firstName} ${client.lastName}`.trim();
}

const projectById = (orgId, id) =>
  db.projects.find((p) => p.id === id && p.organizationId === orgId) || null;

/** Rekord w postaci, ktora widzi klient API (z doklejonymi nazwami zrodel). */
function present(orgId, row) {
  return {
    ...row,
    dealName: row.dealId ? dealLabel(orgId, row.dealId) : null,
    projectName: row.projectId ? projectById(orgId, row.projectId)?.name || null : null,
  };
}

const taskById = (orgId, id) => db.tasks.find((t) => t.id === id && t.organizationId === orgId) || null;

/**
 * Wspolne cialo tworzenia zadania — identyczne dla trzech tras (bez powiazania,
 * pod dealem, w projekcie), bo w board360 rozni je wylacznie adres.
 */
function buildTask(req, res, { dealId = null, projectId = null }) {
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

  if (b.section != null && b.section !== '' && !TASK_SECTIONS.has(b.section)) {
    return unprocessable(res, 'Nieznana sekcja zadania.');
  }
  if (b.slaHours != null && b.slaHours !== '' && !SLA_HOURS.has(Number(b.slaHours))) {
    return unprocessable(res, 'SLA moze wynosic 24, 168 albo 720 godzin.');
  }

  // Hybryda z panelu: sekcja z pola, a gdy go nie ma — z etapu deala.
  const deal = dealId ? dealById(req.user.organizationId, dealId) : null;
  const section = b.section || (deal ? SECTION_BY_STAGE[deal.stage] || null : null);

  const row = {
    id: uuid(),
    organizationId: req.user.organizationId,
    dealId: dealId || b.dealId || null,
    projectId: projectId || null,
    title,
    description: b.description ? String(b.description) : null,
    assigneeId,
    assigneeEmail,
    dueAt: b.dueAt ? new Date(b.dueAt).toISOString() : null,
    status: 'open',
    priority: TASK_PRIORITIES.has(b.priority) ? b.priority : 'normal',
    section,
    estimatedMinutes: b.estimatedMinutes != null && b.estimatedMinutes !== ''
      ? Number(b.estimatedMinutes)
      : null,
    slaHours: b.slaHours != null && b.slaHours !== '' ? Number(b.slaHours) : null,
    commentCount: 0,
    createdBy: req.user.id,
    createdAt: nowIso(),
    updatedAt: nowIso(),
  };
  db.tasks.push(row);
  return res.status(201).json(present(req.user.organizationId, row));
}

// ── Czlonkowie zespolu ───────────────────────────────────────────────────────
// `functions` i `additionalRoles` sa tu obowiazkowe: po nich kreator zadania
// filtruje osoby pod kafelkami zespolow (`TaskTeam.membersFrom`), a bez nich
// kazdy kafelek wyszedlby pusty.
router.get('/tasks/members', requireAuth, requirePermission('tasks.view'), (req, res) => {
  const list = db.users
    .filter((u) => u.organizationId === req.user.organizationId)
    .map((u) => ({
      id: u.id,
      email: u.email,
      firstName: u.firstName || null,
      lastName: u.lastName || null,
      role: u.role,
      additionalRoles: u.additionalRoles || [],
      functions: u.functions || [],
    }));
  res.json(list);
});

// ── Lista i tworzenie ────────────────────────────────────────────────────────
router.get('/tasks', requireAuth, requirePermission('tasks.view'), (req, res) => {
  const statusF = req.query.status ? String(req.query.status) : null;
  const assigneeF = req.query.assignee === 'me' ? req.user.id : req.query.assignee || null;

  let list = db.tasks.filter((t) => t.organizationId === req.user.organizationId);
  if (statusF) list = list.filter((t) => t.status === statusF);
  if (assigneeF) list = list.filter((t) => t.assigneeId === assigneeF);
  list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  res.json(list.map((t) => present(req.user.organizationId, t)));
});

router.post('/tasks', requireAuth, requirePermission('tasks.manage'), (req, res) =>
  buildTask(req, res, {}),
);

/**
 * Jedno zadanie po id — trasa MUSI byc za `/tasks/members`, inaczej "members"
 * wpadnie w `:id`. Karta zadania w telefonie otwiera sie takze z powiadomienia
 * i z odnosnika w dyskusji, wiec nie da sie jej zlozyc z pozycji listy.
 */
router.get('/tasks/:id', requireAuth, requirePermission('tasks.view'), (req, res) => {
  const task = taskById(req.user.organizationId, req.params.id);
  if (!task) return res.status(404).json({ message: 'Zadanie nie istnieje.' });
  return res.json(present(req.user.organizationId, task));
});

// ── Komentarze karty zadania ─────────────────────────────────────────────────
// Komentuje kazdy z `tasks.view` — to wspolpraca, nie zarzadzanie zadaniem.
// `mentions[]` to tokeny wywolan ("user:<id>", "role:<rola>", "watchers",
// "all"); rozwijamy je do userow przy zapisie, tak jak robi to board360.

/** Komentarz w postaci, ktora widzi klient API (z danymi autora). */
function presentComment(orgId, row) {
  const author = userById(orgId, row.authorId);
  return {
    id: row.id,
    taskId: row.taskId,
    authorId: row.authorId,
    authorEmail: author ? author.email : null,
    authorFirstName: author ? author.firstName || null : null,
    authorLastName: author ? author.lastName || null : null,
    body: row.body,
    createdAt: row.createdAt,
  };
}

router.get('/tasks/:id/comments', requireAuth, requirePermission('tasks.view'), (req, res) => {
  const task = taskById(req.user.organizationId, req.params.id);
  if (!task) return res.status(404).json({ message: 'Zadanie nie istnieje.' });
  const list = db.taskComments
    .filter((c) => c.taskId === task.id && c.organizationId === req.user.organizationId)
    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
  return res.json(list.map((c) => presentComment(req.user.organizationId, c)));
});

router.post('/tasks/:id/comments', requireAuth, requirePermission('tasks.view'), (req, res) => {
  const task = taskById(req.user.organizationId, req.params.id);
  if (!task) return res.status(404).json({ message: 'Zadanie nie istnieje.' });
  const body = typeof (req.body || {}).body === 'string' ? req.body.body.trim() : '';
  if (!body) return unprocessable(res, 'Tresc komentarza jest wymagana.', ['tresc komentarza']);

  const row = {
    id: uuid(),
    organizationId: req.user.organizationId,
    taskId: task.id,
    authorId: req.user.id,
    body,
    createdAt: nowIso(),
  };
  db.taskComments.push(row);
  recordMentions(req.user.organizationId, task.id, row.id, req.user.id, (req.body || {}).mentions);
  // Wlasny komentarz nie moze wracac jako nieprzeczytany u autora.
  markDiscussionRead(req.user.organizationId, req.user.id, task.id);
  task.commentCount = db.taskComments.filter((c) => c.taskId === task.id).length;
  task.updatedAt = nowIso();
  return res.status(201).json(presentComment(req.user.organizationId, row));
});

router.delete('/task-comments/:id', requireAuth, requirePermission('tasks.view'), (req, res) => {
  const idx = db.taskComments.findIndex(
    (c) => c.id === req.params.id && c.organizationId === req.user.organizationId,
  );
  if (idx < 0) return res.status(404).json({ message: 'Komentarz nie istnieje.' });
  const [row] = db.taskComments.splice(idx, 1);
  db.taskCommentMentions = db.taskCommentMentions.filter((m) => m.commentId !== row.id);
  const task = taskById(req.user.organizationId, row.taskId);
  if (task) task.commentCount = db.taskComments.filter((c) => c.taskId === task.id).length;
  return res.status(204).end();
});

// ── Zadania pod dealem (tak zadanie wiaze sie z klientem) ────────────────────
router.get('/deals/:id/tasks', requireAuth, requirePermission('tasks.view'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Deal nie istnieje.' });
  const list = db.tasks
    .filter((t) => t.organizationId === req.user.organizationId && t.dealId === deal.id)
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  return res.json(list.map((t) => present(req.user.organizationId, t)));
});

router.post('/deals/:id/tasks', requireAuth, requirePermission('tasks.manage'), (req, res) => {
  const deal = dealById(req.user.organizationId, req.params.id);
  if (!deal) return res.status(404).json({ message: 'Deal nie istnieje.' });
  return buildTask(req, res, { dealId: deal.id });
});

// ── Zmiana i usuniecie ───────────────────────────────────────────────────────
/**
 * PATCH przyjmuje pojedyncze pola — tak samo robi to karta zadania w panelu
 * i tak dziala kolejka offline w aplikacji (jedno pole = jedna zmiana).
 */
router.patch('/tasks/:id', requireAuth, requirePermission('tasks.manage'), (req, res) => {
  const task = taskById(req.user.organizationId, req.params.id);
  if (!task) return res.status(404).json({ message: 'Zadanie nie istnieje.' });
  const b = req.body || {};

  if ('title' in b) {
    const title = typeof b.title === 'string' ? b.title.trim() : '';
    if (!title) return unprocessable(res, 'Tytul jest wymagany.', ['tytul zadania']);
    task.title = title;
  }
  if ('description' in b) task.description = b.description ? String(b.description) : null;
  if ('assigneeId' in b) {
    if (!b.assigneeId) {
      task.assigneeId = null;
      task.assigneeEmail = null;
    } else {
      const member = userById(req.user.organizationId, b.assigneeId);
      if (!member) return unprocessable(res, 'Nieznany pracownik (assigneeId).');
      task.assigneeId = member.id;
      task.assigneeEmail = member.email;
    }
  }
  if ('dueAt' in b) task.dueAt = b.dueAt ? new Date(b.dueAt).toISOString() : null;
  if ('status' in b) {
    if (!TASK_STATUSES.has(b.status)) return unprocessable(res, 'Nieznany status zadania.');
    task.status = b.status;
  }
  if ('priority' in b) {
    if (!TASK_PRIORITIES.has(b.priority)) return unprocessable(res, 'Nieznany priorytet.');
    task.priority = b.priority;
  }
  if ('section' in b) {
    if (b.section && !TASK_SECTIONS.has(b.section)) {
      return unprocessable(res, 'Nieznana sekcja zadania.');
    }
    task.section = b.section || null;
  }
  if ('estimatedMinutes' in b) {
    task.estimatedMinutes =
      b.estimatedMinutes === null || b.estimatedMinutes === '' ? null : Number(b.estimatedMinutes);
  }
  if ('slaHours' in b) {
    if (b.slaHours !== null && b.slaHours !== '' && !SLA_HOURS.has(Number(b.slaHours))) {
      return unprocessable(res, 'SLA moze wynosic 24, 168 albo 720 godzin.');
    }
    task.slaHours = b.slaHours === null || b.slaHours === '' ? null : Number(b.slaHours);
  }

  task.updatedAt = nowIso();
  return res.json(present(req.user.organizationId, task));
});

router.delete('/tasks/:id', requireAuth, requirePermission('tasks.manage'), (req, res) => {
  const idx = db.tasks.findIndex(
    (t) => t.id === req.params.id && t.organizationId === req.user.organizationId,
  );
  if (idx < 0) return res.status(404).json({ message: 'Zadanie nie istnieje.' });
  db.tasks.splice(idx, 1);
  return res.status(204).end();
});

// ── Projekty (krok "kogo dotyczy" w kreatorze) ───────────────────────────────
router.get('/projects', requireAuth, requirePermission('projects.view'), (req, res) => {
  const statusF = req.query.status ? String(req.query.status) : null;
  // `templates=0` (domyslnie) chowa szablony projektow — tak pyta mobilka.
  const withTemplates = String(req.query.templates || '0') === '1';

  let list = db.projects.filter((p) => p.organizationId === req.user.organizationId);
  if (statusF) list = list.filter((p) => p.status === statusF);
  if (!withTemplates) list = list.filter((p) => !p.isTemplate);

  res.json(
    list.map((p) => ({
      id: p.id,
      name: p.name,
      status: p.status,
      color: p.color,
      taskCount: db.tasks.filter((t) => t.projectId === p.id).length,
    })),
  );
});

router.post('/projects/:id/tasks', requireAuth, requirePermission('projects.manage'), (req, res) => {
  const project = projectById(req.user.organizationId, req.params.id);
  if (!project) return res.status(404).json({ message: 'Projekt nie istnieje.' });
  return buildTask(req, res, { projectId: project.id });
});

module.exports = router;
