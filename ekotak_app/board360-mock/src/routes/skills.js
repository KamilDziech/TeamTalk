'use strict';
/*
 * Poziomy umiejetnosci (board360: modul Zespol -> "Matryca poziomow").
 *
 * Mobilka czyta stad JEDNA rzecz: kto nie dowozi wymogu w domenie audytu.
 * Selektor osoby wykonujacej wizje filtruje po UPRAWNIENIU (umiejetnosc
 * "Audyt" w `skills`), a to dokłada notke "(do nadgonienia: ...)". Endpoint
 * celowo NIE ma `requirePermission`: w board360 tez go nie ma, bo koordynator
 * wybierajacy wykonawce ma widziec luki, nie majac dostepu do calej matrycy.
 */

const express = require('express');
const { requireAuth } = require('../middleware');
const { db } = require('../store');

const router = express.Router();

// Luki wpisane na sztywno per e-mail: matryca poziomow to osobny modul, ktorego
// atrapa nie odwzorowuje, a do sprawdzenia notki wystarczy jeden brakujacy
// szczebel. Tokeny maja ksztalt `poziom:czesc`, tak jak w board360.
const GAPS = {
  'biz-audyt': {
    'koordynator@ekotak.pl': ['sredni:praktyka'],
  },
};

router.get('/domain-skills/coverage', requireAuth, (req, res) => {
  const domainId = String(req.query.domain || '').trim();
  const gaps = GAPS[domainId] || {};
  const subjects = db.users
    .filter((u) => u.organizationId === req.user.organizationId)
    .map((u) => ({
      subjectId: u.id,
      requiredLevel: null,
      missing: gaps[u.email] || [],
      assessed: true,
    }));
  res.json({ data: { subjects } });
});

module.exports = router;
