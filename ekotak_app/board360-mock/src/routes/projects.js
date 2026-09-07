'use strict';
/*
 * Modul Projekt (board360 `api/src/modules/projects`) — tyle, ile czyta
 * mobilka: karta projektu jednym strzalem, pomysl do Poczekalni i domkniecie
 * zadania z podaniem czasu.
 *
 * Podzial z `routes/tasks.js`: tam zostaja dwie trasy obslugujace KREATOR
 * ZADANIA (`GET /api/projects` — wybor projektu w kroku "kogo dotyczy" —
 * i `POST /api/projects/:id/tasks`), tutaj jest sam modul Projekt.
 *
 * Czego tu nie ma, bo to zakladki PANELU i telefon ich nie otwiera: sekcje,
 * zaleznosci zadan (Gantt), wycena i budzet (`projects.finance`), poczekalnia
 * z przenoszeniem pomyslow, szablony, obciazenie zespolu, stawki rol.
 *
 * UPRAWNIENIA po poluzowaniu z 2026-09-06 (board360 `permissions.ts`) — te trzy
 * trasy chodza pod `projects.view`, bo wykonawca w terenie ma domknac zadanie
 * i zglosic pomysl z telefonu. Zostaly DWIE granice, obie do sprawdzenia na
 * koncie `serwisant@ekotak.pl`:
 *   - `POST /projects` z czyms innym niz goly pomysl (`status != 'idea'`
 *     albo szablon) → 403 bez `projects.manage`;
 *   - `POST /projects/tasks/:id/close` na CUDZYM zadaniu → 403 bez
 *     `projects.manage`; wlasne domyka sie normalnie.
 */

const express = require('express');
const { uuid, nowIso } = require('../crypto');
const { requireAuth, requirePermission, unprocessable } = require('../middleware');
const { permsFor } = require('../rbac');
const {
  db,
  userById,
  taskById,
  dealById,
  projectById,
  presentProject,
  activateDueTasks,
} = require('../store');

const router = express.Router();

const PROJECT_STATUSES = new Set(['active', 'archived', 'idea']);
/** Gorna granica z `taskCloseSchema` board360 — powyzej leci 422. */
const MAX_ACTUAL_MINUTES = 100000;

/**
 * Zadanie w rzucie modulu Projekt. Te same wiersze co `GET /api/tasks`, ale
 * inne pola: dochodzi cykl zycia, kamien milowy i czas z domkniecia, znika
 * cala obudowa listy zadan (sekcja, SLA, komentarze).
 */
function presentProjectTask(row) {
  return {
    id: row.id,
    projectId: row.projectId,
    title: row.title,
    assigneeId: row.assigneeId,
    assigneeEmail: row.assigneeEmail,
    startAt: row.startAt || null,
    dueAt: row.dueAt,
    status: row.status,
    // `planned` zyje tylko w projekcie, `active` jest przekazane do realizacji
    // i widac je w module Zadania. Starsze wiersze bez kolumny czytamy jak aktywne.
    lifecycle: row.lifecycle || 'active',
    milestoneId: row.milestoneId || null,
    estimatedMinutes: row.estimatedMinutes,
    actualMinutes: row.actualMinutes == null ? null : row.actualMinutes,
  };
}

/** Kamien milowy z licznikami zadan — z nich mobilka rysuje pasek postepu. */
function presentMilestone(orgId, row) {
  const tasks = db.tasks.filter(
    (t) => t.organizationId === orgId && t.milestoneId === row.id,
  );
  const owner = row.ownerId ? userById(orgId, row.ownerId) : null;
  return {
    id: row.id,
    projectId: row.projectId,
    name: row.name,
    dueAt: row.dueAt || null,
    acceptanceCriteria: row.acceptanceCriteria || null,
    ownerEmail: owner ? owner.email : null,
    doneAt: row.doneAt || null,
    position: row.position,
    taskCount: tasks.length,
    doneCount: tasks.filter((t) => t.status === 'done').length,
  };
}

function presentMember(orgId, row) {
  const user = userById(orgId, row.userId);
  return {
    userId: row.userId,
    email: user ? user.email : null,
    firstName: user ? user.firstName || null : null,
    lastName: user ? user.lastName || null : null,
    role: row.role,
  };
}

// ── Projekty deala (zakladka "Harmonogram" karty) ────────────────────────────
// MUSI stac PRZED `/projects/:id`, inaczej Express wzialby "by-deal" za id
// projektu i oddal 404 — dokladnie ta sama kolejnosc co w board360.
//
// Zwracamy takze projekty ZARCHIWIZOWANE: panel pokazuje je z dopiskiem
// "· archiwum", bo historia projektu jest czescia historii deala. Kolejnosc jak
// w `listByDeal` board360: najpierw aktywne, potem archiwum.
router.get(
  '/projects/by-deal/:dealId',
  requireAuth,
  requirePermission('projects.view'),
  (req, res) => {
    const orgId = req.user.organizationId;
    activateDueTasks(orgId);
    const rows = db.projects
      .filter((p) => p.organizationId === orgId && p.dealId === req.params.dealId)
      .sort(
        (a, b) =>
          String(a.status).localeCompare(String(b.status)) ||
          String(b.updatedAt).localeCompare(String(a.updatedAt)),
      );
    return res.json(rows.map((p) => presentProject(orgId, p)));
  },
);

// ── Karta projektu ───────────────────────────────────────────────────────────
// Jeden strzal na cala karte: naglowek, kamienie, zadania i zespol. Mobilka nie
// dopytuje o nic wiecej, bo w kotlowni kazdy dodatkowy request to kolejna szansa
// na brak zasiegu.
router.get('/projects/:id', requireAuth, requirePermission('projects.view'), (req, res) => {
  const orgId = req.user.organizationId;
  const project = projectById(orgId, req.params.id);
  if (!project) return res.status(404).json({ message: 'Projekt nie istnieje.' });
  activateDueTasks(orgId);

  const milestones = db.projectMilestones
    .filter((m) => m.organizationId === orgId && m.projectId === project.id)
    .sort((a, b) => a.position - b.position)
    .map((m) => presentMilestone(orgId, m));

  const tasks = db.tasks
    .filter((t) => t.organizationId === orgId && t.projectId === project.id)
    .map(presentProjectTask);

  const members = db.projectMembers
    .filter((m) => m.organizationId === orgId && m.projectId === project.id)
    .map((m) => presentMember(orgId, m));

  return res.json({ ...presentProject(orgId, project), milestones, tasks, members });
});

// ── Pomysl do Poczekalni ─────────────────────────────────────────────────────
// Najkrotsza droga z telefonu do modulu: nazwa, opcjonalny opis i dzial.
// Etap wynika ze statusu — pomysl ląduje w Poczekalni (`idea`), wszystko inne
// startuje od Oceny (`appraisal`) i dostaje autora jako prowadzacego.
//
// Pomysl zglasza KAZDY, kto widzi modul — to skrzynka "co mnie wkurza", nie
// planowanie. Zalozenie projektu wprost (i szablonu) nadal wymaga `projects.manage`.
router.post('/projects', requireAuth, requirePermission('projects.view'), (req, res) => {
  const b = req.body || {};
  const name = typeof b.name === 'string' ? b.name.trim() : '';
  if (!name) return unprocessable(res, 'Nazwa jest wymagana.', ['nazwa projektu']);
  if (b.status != null && b.status !== '' && !PROJECT_STATUSES.has(b.status)) {
    return unprocessable(res, 'Status projektu moze byc active, archived albo idea.');
  }

  const status = b.status || 'active';
  const isIdea = status === 'idea';
  if ((!isIdea || b.isTemplate) && !permsFor(req.user.role).includes('projects.manage')) {
    return res
      .status(403)
      .json({ message: 'Bez uprawnienia do projektow mozesz zglosic tylko pomysl.' });
  }
  // Projekt zakladany z zakladki "Harmonogram" karty deala przychodzi z `dealId`.
  // Nieznany deal odrzucamy tak jak board360 (obce id nie moze zawisnac w bazie),
  // ale zostawiamy przy tym 422 z nazwa pola — mobilka umie to pokazac po polsku.
  let dealId = null;
  if (b.dealId != null && b.dealId !== '') {
    const deal = dealById(req.user.organizationId, String(b.dealId));
    if (!deal) return unprocessable(res, 'Deal nie istnieje.', ['deal']);
    dealId = deal.id;
  }
  const row = {
    id: uuid(),
    organizationId: req.user.organizationId,
    name,
    description: b.description ? String(b.description) : null,
    color: b.color ? String(b.color) : null,
    status,
    dealId,
    isTemplate: Boolean(b.isTemplate),
    stage: isIdea ? 'idea' : 'appraisal',
    department: b.department ? String(b.department) : null,
    // Pomysl nie dostaje prowadzacego — kto go poprowadzi, ustala sie dopiero
    // przy wzieciu do oceny. Projekt zakladany wprost prowadzi jego autor.
    managerId: isIdea ? null : req.user.id,
    sponsorId: null,
    problemStatement: null,
    metricName: null,
    metricBaseline: null,
    metricTarget: null,
    dueAt: null,
    createdBy: req.user.id,
    createdAt: nowIso(),
    updatedAt: nowIso(),
  };
  db.projects.push(row);
  return res.status(201).json(presentProject(req.user.organizationId, row));
});

// ── Domkniecie zadania z podaniem czasu ──────────────────────────────────────
// `actualMinutes` puste znaczy "nie podano" — rozliczenie policzy zadanie po
// estymacie. CELOWO bez blokady: wymuszanie liczby odstraszyloby ludzi od
// domykania zadan, a to szkodzi bardziej niz zadania wiszace otwarte.
//
// Wykonawca domyka z telefonu WLASNE zadanie; cudze zostaje pod `projects.manage`.
// Zadanie bez wykonawcy nie jest niczyje — tez wymaga zarzadzania.
router.post(
  '/projects/tasks/:taskId/close',
  requireAuth,
  requirePermission('projects.view'),
  (req, res) => {
    const task = taskById(req.user.organizationId, req.params.taskId);
    if (!task) return res.status(404).json({ message: 'Zadanie nie istnieje.' });
    if (!permsFor(req.user.role).includes('projects.manage') && task.assigneeId !== req.user.id) {
      return res.status(403).json({ message: 'To zadanie nie jest przypisane do Ciebie.' });
    }

    const raw = (req.body || {}).actualMinutes;
    let actualMinutes = null;
    if (raw != null && raw !== '') {
      const minutes = Number(raw);
      if (!Number.isInteger(minutes) || minutes < 0 || minutes > MAX_ACTUAL_MINUTES) {
        return unprocessable(res, 'Czas wykonania podaje sie w pelnych minutach.', ['czas wykonania']);
      }
      actualMinutes = minutes;
    }

    task.status = 'done';
    task.actualMinutes = actualMinutes;
    task.updatedAt = nowIso();
    // `projectId` w odpowiedzi jest OBOWIAZKOWE: mobilka wklada zwrocone
    // zadanie z powrotem do karty projektu i bez tego pola zgubilaby je z listy.
    return res.json(presentProjectTask(task));
  },
);

module.exports = router;
