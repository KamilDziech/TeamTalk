'use strict';
/* Logowanie mobilne (prompt A1) i tozsamosc zalogowanego uzytkownika. */

const express = require('express');
const { MOBILE_SESSION_TTL } = require('../config');
const { issueToken, verifyPassword } = require('../crypto');
const { authContext } = require('../rbac');
const { requireAuth } = require('../middleware');
const { db } = require('../store');

const router = express.Router();

router.post('/auth/mobile-login', (req, res) => {
  const { email, password } = req.body || {};
  if (!email || !password) return res.status(400).json({ message: 'email i password sa wymagane.' });

  const user = db.users.find((u) => u.email.toLowerCase() === String(email).trim().toLowerCase());
  if (!user || !verifyPassword(user.passwordHash, password)) {
    return res.status(401).json({ message: 'Nieprawidlowy e-mail lub haslo.' });
  }

  const exp = Math.floor(Date.now() / 1000) + MOBILE_SESSION_TTL;
  const token = issueToken({
    sub: user.email,
    userId: user.id,
    organizationId: user.organizationId,
    role: user.role,
    exp,
  });
  return res.json({ token, expiresAt: exp, user: authContext(user) });
});

router.get('/me', requireAuth, (req, res) => res.json(authContext(req.user)));

module.exports = router;
