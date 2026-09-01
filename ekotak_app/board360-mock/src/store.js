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
  mainCategoryOf,
  logActivity,
  visibleDeals,
};
