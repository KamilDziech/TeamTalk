'use strict';
/*
 * Baza w pamieci + helpery czytane przez trasy. Restart = powrot do seeda.
 */

const { uuid, nowIso } = require('./crypto');

const db = {
  organization: { id: uuid(), name: 'EKOTAK' },
  users: [],
  clients: [],
  callLogs: [],
  voiceReports: [],
  devices: [],
  tasks: [],
  projects: [],            // projekty — krok "kogo dotyczy" w kreatorze zadania
  // ── Komentarze zadan i Komunikator wewnetrzny ──────────────────────────────
  // Dyskusja w Komunikatorze = watek komentarzy JEDNEGO zadania (board360 nie
  // ma osobnej tabeli dyskusji). Wzmianki rozwiniete do userow przy zapisie.
  taskComments: [],        // {id, organizationId, taskId, authorId, body, createdAt}
  // Metadane zalacznikow; tresc lezy na dysku w UPLOADS_DIR pod `storageKey`.
  taskAttachments: [],     // {id, organizationId, taskId, name, storageKey, size, contentType, uploadedBy, createdAt}
  taskCommentMentions: [], // {id, organizationId, taskId, commentId, userId, createdAt}
  discussionReads: [],     // {organizationId, userId, taskId, lastReadAt}
  // ── CRM ────────────────────────────────────────────────────────────────────
  categories: [],          // katalog technologii (GET /api/categories)
  deals: [],
  dealContacts: [],        // {dealId, clientId} — kontakty towarzyszace
  dealValues: {},          // dealId -> kwota brutto (GET /api/offers/deal-values)
  dealInstallations: {},   // dealId -> { [stage]: [categoryId] } (wybor per etap)
  activities: [],          // ActivityLog (append-only)
  leads: [],               // zgloszenia z leadowni, po jednym na deal
};

const normalizePhone = (p) => String(p || '').replace(/[^0-9]/g, '').replace(/^0+/, '');

function findClientByPhone(orgId, phone) {
  const n = normalizePhone(phone);
  if (!n) return null;
  return (
    db.clients.find(
      (c) =>
        c.organizationId === orgId &&
        (normalizePhone(c.phone).endsWith(n) ||
          normalizePhone(c.phone2).endsWith(n) ||
          (normalizePhone(c.phone) && n.endsWith(normalizePhone(c.phone)))),
    ) || null
  );
}

const clientById = (orgId, id) => db.clients.find((c) => c.id === id && c.organizationId === orgId) || null;
const dealById = (orgId, id) => db.deals.find((d) => d.id === id && d.organizationId === orgId) || null;
const userById = (orgId, id) => db.users.find((u) => u.id === id && u.organizationId === orgId) || null;
const taskById = (orgId, id) => db.tasks.find((t) => t.id === id && t.organizationId === orgId) || null;

/** "Imie Nazwisko" albo e-mail — tak podpisuje autora komentarza panel. */
function userLabel(user) {
  if (!user) return null;
  const full = `${user.firstName || ''} ${user.lastName || ''}`.trim();
  return full || user.email;
}

/**
 * Podpis dyskusji w Komunikatorze (ustalenia 2026-09-01, board360
 * `DiscussionsService.labels`): zadanie pod dealem → "Nazwisko · kod deala",
 * zadanie w projekcie → nazwa projektu, luzne → tytul zadania. Zadanie nie ma
 * `clientId` — z klientem wiaze je wylacznie deal.
 */
function discussionLabel(orgId, task) {
  const deal = task.dealId ? dealById(orgId, task.dealId) : null;
  const client = deal ? clientById(orgId, deal.clientId) : null;
  const project = task.projectId
    ? db.projects.find((p) => p.id === task.projectId && p.organizationId === orgId) || null
    : null;

  const surname = (client && (client.lastName || '').trim()) || '';
  const given = (client && (client.firstName || '').trim()) || '';
  const short = surname || given;
  const full = `${given} ${surname}`.trim();

  return {
    title: deal && short ? `${short} · ${deal.code}` : project ? project.name : task.title,
    clientId: (deal && deal.clientId) || null,
    clientName: full || null,
    dealId: (deal && deal.id) || null,
    dealCode: (deal && deal.code) || null,
    projectId: task.projectId || null,
    projectName: project ? project.name : null,
  };
}

/** Kategoria glowna (parentId === null), do ktorej nalezy dowolny wezel katalogu. */
function mainCategoryOf(categoryId) {
  let node = db.categories.find((c) => c.id === categoryId);
  const seen = new Set();
  while (node && node.parentId && !seen.has(node.id)) {
    seen.add(node.id);
    node = db.categories.find((c) => c.id === node.parentId);
  }
  return node || null;
}

/**
 * Funkcje niosace dana role RBAC (board360 `FUNCTION_ROLE`) — po rozdzieleniu
 * roli i funkcji samo `role: 'koordynator'` nie znalazloby juz nikogo.
 */
const FUNCTION_ROLE = { koordynator: 'koordynator', serwis: 'serwisant', inzynier: 'inzynier' };

const KNOWN_ROLES = new Set(['admin', 'zarzad', 'koordynator', 'serwisant', 'biuro', 'montaz']);

/**
 * Rozwija tokeny wzmianek ("user:<id>", "role:<rola>", "watchers", "all") do
 * id userow, z pominieciem autora — 1:1 z `DiscussionsService.expandTokens`.
 */
function expandMentionTokens(orgId, taskId, authorId, tokens) {
  const ids = new Set();
  const roles = new Set();
  let all = false;
  let watchers = false;

  for (const raw of tokens || []) {
    const t = String(raw || '').trim();
    if (t === 'all') all = true;
    else if (t === 'watchers') watchers = true;
    else if (t.startsWith('user:')) ids.add(t.slice(5));
    else if (t.startsWith('role:') && KNOWN_ROLES.has(t.slice(5))) roles.add(t.slice(5));
  }

  const orgUsers = db.users.filter((u) => u.organizationId === orgId);
  if (all) {
    orgUsers.forEach((u) => ids.add(u.id));
  } else if (roles.size > 0) {
    const fns = Object.keys(FUNCTION_ROLE).filter((f) => roles.has(FUNCTION_ROLE[f]));
    orgUsers
      .filter(
        (u) =>
          roles.has(u.role) ||
          (u.additionalRoles || []).some((r) => roles.has(r)) ||
          (u.functions || []).some((f) => fns.includes(f)),
      )
      .forEach((u) => ids.add(u.id));
  }

  if (watchers) {
    const task = taskById(orgId, taskId);
    if (task && task.assigneeId) ids.add(task.assigneeId);
    if (task && task.createdBy) ids.add(task.createdBy);
    db.taskComments.filter((c) => c.taskId === taskId).forEach((c) => ids.add(c.authorId));
    db.taskCommentMentions.filter((m) => m.taskId === taskId).forEach((m) => ids.add(m.userId));
  }

  ids.delete(authorId);
  // Odsiewamy martwe id — token moze przyjsc z nieaktualnej listy w telefonie.
  return [...ids].filter((id) => orgUsers.some((u) => u.id === id));
}

/** Zapisuje wywolania dla swiezo dodanego komentarza (po rozwinieciu tokenow). */
function recordMentions(orgId, taskId, commentId, authorId, tokens) {
  const userIds = expandMentionTokens(orgId, taskId, authorId, tokens);
  for (const userId of userIds) {
    db.taskCommentMentions.push({
      id: uuid(),
      organizationId: orgId,
      taskId,
      commentId,
      userId,
      createdAt: nowIso(),
    });
  }
  return userIds;
}

/** Znacznik przeczytania dyskusji — do licznikow nieprzeczytanych. */
function markDiscussionRead(orgId, userId, taskId) {
  const row = db.discussionReads.find(
    (r) => r.organizationId === orgId && r.userId === userId && r.taskId === taskId,
  );
  if (row) row.lastReadAt = nowIso();
  else db.discussionReads.push({ organizationId: orgId, userId, taskId, lastReadAt: nowIso() });
}

/** Dopisuje wpis do historii deala (zakladka "historia" karty). */
function logActivity(user, dealId, action, diff) {
  const row = {
    id: uuid(),
    organizationId: user.organizationId,
    dealId,
    action,
    userId: user.id,
    createdAt: nowIso(),
    diff: diff === undefined ? null : diff,
  };
  db.activities.push(row);
  return row;
}

/** Deale widoczne dla uzytkownika (clientVisibility === 'own' zawezasa do swoich). */
function visibleDeals(user) {
  let list = db.deals.filter((d) => d.organizationId === user.organizationId);
  if (user.clientVisibility === 'own') list = list.filter((d) => d.ownerId === user.id);
  return list;
}

module.exports = {
  db,
  normalizePhone,
  findClientByPhone,
  clientById,
  dealById,
  userById,
  taskById,
  userLabel,
  discussionLabel,
  expandMentionTokens,
  recordMentions,
  markDiscussionRead,
  mainCategoryOf,
  logActivity,
  visibleDeals,
};
