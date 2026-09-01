'use strict';
/*
 * Token sesji i hasla — 1:1 z board360 (`web/src/lib/auth.ts`).
 *
 * Token = base64url(JSON{sub,userId,organizationId,role,exp}).HMAC-SHA256
 * Hasla: scrypt (bez natywnych zaleznosci, wiec obraz Dockera zostaje maly).
 */

const crypto = require('crypto');
const { SESSION_SECRET } = require('./config');

const uuid = () => crypto.randomUUID();
const nowIso = () => new Date().toISOString();

const b64url = (buf) => Buffer.from(buf).toString('base64url');
const sign = (payload) => crypto.createHmac('sha256', SESSION_SECRET).update(payload).digest('base64url');

function issueToken({ sub, userId, organizationId, role, exp }) {
  const payload = b64url(JSON.stringify({ sub, userId, organizationId, role, exp }));
  return `${payload}.${sign(payload)}`;
}

function verifyToken(token) {
  if (!token) return null;
  const [payload, sig] = String(token).split('.');
  if (!payload || !sig) return null;
  const a = Buffer.from(sig);
  const b = Buffer.from(sign(payload));
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return null;
  let data;
  try {
    data = JSON.parse(Buffer.from(payload, 'base64url').toString());
  } catch {
    return null;
  }
  if (typeof data.exp !== 'number' || data.exp < Math.floor(Date.now() / 1000)) return null;
  return data;
}

function hashPassword(pw) {
  const salt = crypto.randomBytes(16);
  const dk = crypto.scryptSync(pw, salt, 32);
  return `scrypt$${salt.toString('hex')}$${dk.toString('hex')}`;
}

function verifyPassword(stored, pw) {
  const parts = String(stored).split('$');
  if (parts.length !== 3) return false;
  const dk = crypto.scryptSync(pw, Buffer.from(parts[1], 'hex'), 32);
  const exp = Buffer.from(parts[2], 'hex');
  return dk.length === exp.length && crypto.timingSafeEqual(dk, exp);
}

module.exports = { uuid, nowIso, issueToken, verifyToken, hashPassword, verifyPassword };
