'use strict';
/*
 * Preferencje UI zalogowanego (klucz -> wartosc), jak `PreferencesController`
 * board360. Kazdy zarzadza wylacznie swoimi ustawieniami — zadnego dodatkowego
 * uprawnienia, wystarczy wazna sesja. Klucze sa na zamknietej liscie: nieznany
 * dostaje 400, dokladnie tak jak w prawdziwym API.
 *
 * TeamTalk czyta stad `tasks.order` — reczna kolejnosc zadan ukladana w panelu
 * mysza, a na telefonie przeciaganiem w zakladce „Zadania" karty deala.
 */

const express = require('express');
const { requireAuth } = require('../middleware');
const { db } = require('../store');

const router = express.Router();

/** Whitelist kluczy — lustro `PREFERENCE_KEYS` z api/preferences. */
const PREFERENCE_KEYS = new Set(['tasks.order']);

/** Limit wartosci; miesci liste id (kolejnosc zadan calej organizacji). */
const MAX_VALUE_LENGTH = 20000;

function store() {
  if (!Array.isArray(db.preferences)) db.preferences = [];
  return db.preferences;
}

router.get('/me/preferences/:key', requireAuth, (req, res) => {
  const key = req.params.key;
  if (!PREFERENCE_KEYS.has(key)) {
    return res.status(400).json({ message: 'Nieznany klucz preferencji.' });
  }
  const row = store().find((p) => p.userId === req.user.id && p.key === key);
  return res.json({ key, value: row ? row.value : null });
});

router.put('/me/preferences/:key', requireAuth, (req, res) => {
  const key = req.params.key;
  if (!PREFERENCE_KEYS.has(key)) {
    return res.status(400).json({ message: 'Nieznany klucz preferencji.' });
  }
  const value = (req.body || {}).value;
  if (typeof value !== 'string' || value.length > MAX_VALUE_LENGTH) {
    return res.status(422).json({ message: 'Wartosc musi byc tekstem do 20 000 znakow.' });
  }
  const rows = store();
  const row = rows.find((p) => p.userId === req.user.id && p.key === key);
  if (row) row.value = value;
  else rows.push({ userId: req.user.id, key, value });
  return res.json({ key, value });
});

module.exports = router;
