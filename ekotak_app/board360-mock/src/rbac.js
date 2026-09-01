'use strict';
/*
 * Uprawnienia — uproszczone odwzorowanie board360 (prompt A4).
 * `settings.company` steruje anonimizacja RODO w karcie klienta,
 * `deal.manage` wszystkimi zapisami w CRM.
 */

const ALL_PERMS = [
  'crm.view',
  'deal.manage',
  'telephony.use',
  'settings.team',
  'settings.company',
  'reports.view',
  'tasks.view',
  'tasks.manage',
  'projects.view',
  'projects.manage',
];

const ROLE_PERMS = {
  admin: ALL_PERMS,
  zarzad: ALL_PERMS,
  koordynator: [
    'crm.view', 'deal.manage', 'telephony.use', 'reports.view',
    'tasks.view', 'tasks.manage', 'projects.view', 'projects.manage',
  ],
  // Serwisant widzi projekty, ale nie zaklada w nich zadan — na tym koncie da sie
  // na telefonie sprawdzic, ze krok "projekt" w kreatorze konczy sie kodem 403.
  serwisant: ['crm.view', 'telephony.use', 'tasks.view', 'tasks.manage', 'projects.view'],
  biuro: ['crm.view', 'deal.manage', 'tasks.view', 'tasks.manage', 'projects.view'],
  montaz: ['crm.view', 'tasks.view', 'tasks.manage', 'projects.view'],
  stazysta: [],
};

const permsFor = (role) => ROLE_PERMS[role] || [];

/** AuthContext board360 — to samo cialo zwraca mobile-login i GET /api/me. */
const authContext = (user) => ({
  userId: user.id,
  organizationId: user.organizationId,
  email: user.email,
  role: user.role,
  permissions: permsFor(user.role),
  clientVisibility: user.clientVisibility || 'all',
});

module.exports = { ALL_PERMS, ROLE_PERMS, permsFor, authContext };
