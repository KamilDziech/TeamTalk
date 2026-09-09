'use strict';
/*
 * Zakladka „Faktura" karty deala w TeamTalku — dwa zrodla.
 *
 *  1. Montaze deala (`GET /api/installations?dealId=`) — panel rysuje je pod
 *     drzewem instalacji na tej samej zakladce (`MontazPanel` w board360), wiec
 *     telefon czyta dokladnie to samo. Trasa mieszka w `routes/installations.js`
 *     (jedna lista dla zakladek „Montaz" i „Faktura"); rezerwacje terminu
 *     (`reserved`) odsiewa KLIENT, tak samo jak panel.
 *  2. `GET /api/ksef/deals/:dealId/invoices` — faktury sprzedazowe wystawione
 *     klientowi tego deala. KSeF nie wie nic o dealach, wiec dopasowanie idzie
 *     po NIP z danych do faktury, a przy jego braku po nazwie nabywcy; kazdy
 *     wiersz mowi, ktore to bylo dopasowanie (`match`), zeby telefon nie
 *     przedstawial zgadywanki jako pewnika. To jest 1:1 z
 *     `ListDealKsefInvoices` w board360.
 *
 * Uprawnienia jak w board360: montaze `installation.view`, faktury `ksef.view`
 * (ksiegowosc — admin/zarzad/biuro). Kwot z umowy ta trasa nie liczy: telefon
 * ma je z zakladki „Umowa".
 */

const express = require('express');
const { requireAuth, requirePermission } = require('../middleware');
const { db, dealById, clientById } = require('../store');

const router = express.Router();

const invoiceView = (f, match) => ({
  id: f.id,
  ksefNumber: f.ksefNumber,
  direction: f.direction,
  invoiceNumber: f.invoiceNumber,
  issueDate: f.issueDate,
  issuerName: f.issuerName,
  issuerNip: f.issuerNip,
  buyerName: f.buyerName,
  buyerNip: f.buyerNip,
  netAmount: f.netAmount,
  vatAmount: f.vatAmount,
  grossAmount: f.grossAmount,
  currency: f.currency,
  acquisitionTimestamp: f.acquisitionTimestamp,
  fetchedAt: f.fetchedAt,
  match,
});

/** NIP bez myslnikow i spacji; null, gdy nie zostalo 10 cyfr. */
const nipDigits = (value) => {
  const digits = String(value || '').replace(/\D/g, '');
  return digits.length === 10 ? digits : null;
};

router.get(
  '/ksef/deals/:dealId/invoices',
  requireAuth,
  requirePermission('ksef.view'),
  (req, res) => {
    const orgId = req.user.organizationId;
    const deal = dealById(orgId, req.params.dealId);
    if (!deal) return res.status(404).json({ message: 'Nie znaleziono deala.' });

    const client = clientById(orgId, deal.clientId);
    const clientName = client ? `${client.firstName || ''} ${client.lastName || ''}`.trim() : '';
    const names = [deal.billingCompany, deal.billingName, clientName]
      .map((n) => String(n || '').trim())
      .filter((n) => n.length > 0);
    const nip = nipDigits(deal.billingNip);

    const lower = names.map((n) => n.toLowerCase());
    const rows = db.ksefInvoices
      .filter((f) => f.organizationId === orgId && f.direction === 'sales')
      .filter((f) => {
        if (nip && nipDigits(f.buyerNip) === nip) return true;
        return lower.includes(String(f.buyerName || '').trim().toLowerCase());
      })
      .sort((a, b) => String(b.issueDate || '').localeCompare(String(a.issueDate || '')));

    res.json({
      buyer: { nip, label: names[0] || clientName },
      invoices: rows.map((f) =>
        invoiceView(f, nip && nipDigits(f.buyerNip) === nip ? 'nip' : 'name'),
      ),
    });
  },
);

module.exports = router;
