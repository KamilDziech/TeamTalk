'use strict';
/*
 * Katalog technologii i wartosci ofert — dane pochodne, ktorych kartoteka
 * uzywa do badge'ow instalacji i kwot na karcie klienta.
 */

const express = require('express');
const { requireAuth, requirePermission } = require('../middleware');
const { db, visibleDeals } = require('../store');

const router = express.Router();

router.get('/categories', requireAuth, requirePermission('crm.view'), (req, res) => {
  const list = [...db.categories].sort(
    (a, b) => (a.parentId || '').localeCompare(b.parentId || '') || a.position - b.position,
  );
  res.json(list);
});

/** dealId -> kwota brutto. Deale bez oferty po prostu nie maja klucza. */
router.get('/offers/deal-values', requireAuth, requirePermission('crm.view'), (req, res) => {
  const out = {};
  for (const deal of visibleDeals(req.user)) {
    if (deal.id in db.dealValues) out[deal.id] = db.dealValues[deal.id];
  }
  res.json(out);
});

module.exports = router;
