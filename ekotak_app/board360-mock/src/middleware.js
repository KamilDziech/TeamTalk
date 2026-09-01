'use strict';
/*
 * Uwierzytelnienie i uprawnienia. Mobilka wysyla `Authorization: Bearer`,
 * panel web to samo cookie `b360_session` — akceptujemy oba.
 */

const { SESSION_COOKIE } = require('./config');
const { verifyToken } = require('./crypto');
const { permsFor } = require('./rbac');
const { db } = require('./store');

function parseCookies(header) {
  const out = {};
  if (!header) return out;
  for (const part of header.split(';')) {
    const idx = part.indexOf('=');
    if (idx < 0) continue;
    const k = part.slice(0, idx).trim();
    if (!k) continue;
    out[k] = decodeURIComponent(part.slice(idx + 1).trim());
  }
  return out;
}

function requireAuth(req, res, next) {
  const cookieTok = parseCookies(req.headers.cookie)[SESSION_COOKIE];
  const authHeader = req.headers.authorization || '';
  const bearer = authHeader.startsWith('Bearer ') ? authHeader.slice(7) : null;
  const payload = verifyToken(cookieTok || bearer);
  if (!payload) return res.status(401).json({ message: 'Wymagane logowanie.' });
  const user = db.users.find((u) => u.id === payload.userId && u.organizationId === payload.organizationId);
  if (!user) return res.status(401).json({ message: 'Wymagane logowanie.' });
  req.user = user;
  return next();
}

const requirePermission = (perm) => (req, res, next) => {
  if (!permsFor(req.user.role).includes(perm)) {
    return res.status(403).json({ message: `Brak uprawnienia: ${perm}` });
  }
  return next();
};

const can = (user, perm) => permsFor(user.role).includes(perm);

/** 422 w formacie board360 — `missing[]` laduje wprost na ekranie telefonu. */
const unprocessable = (res, message, missing) =>
  res.status(422).json(missing && missing.length ? { message, missing } : { message });

module.exports = { parseCookies, requireAuth, requirePermission, can, unprocessable };
