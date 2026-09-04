'use strict';
/*
 * board360-mock — testowy backend zgodny z kontraktem board360 dla aplikacji
 * mobilnej TeamTalk. Dane w pamieci (seed przy starcie), nagrania na dysku.
 *
 * Cel: testy end-to-end aplikacji Android bez czekania na wdrozenie board360
 * oraz zywa referencja kontraktu (prompty A1-A5) dla autora board360.
 *
 * Auth = format board360: podpisany token HMAC-SHA256 w cookie `b360_session`
 * = base64url(JSON{sub,userId,organizationId,role,exp}).<hmac>. Akceptujemy
 * takze `Authorization: Bearer <token>` (opcja A1b), z ktorego korzysta mobilka.
 *
 * Zrodlo prawdy dla ksztaltu odpowiedzi: `TeamTalkApi.kt` i DTO aplikacji.
 */

const express = require('express');
const fs = require('fs');

const { PORT, HOST, UPLOADS_DIR, LOG_REQUESTS, STAGE_GATES, SESSION_SECRET } = require('./src/config');
const { nowIso } = require('./src/crypto');
const { db } = require('./src/store');
const { seed } = require('./src/seed');

fs.mkdirSync(UPLOADS_DIR, { recursive: true });
seed(db);

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '2mb' }));

if (LOG_REQUESTS) {
  app.use((req, _res, next) => {
    console.log(`${nowIso()} ${req.method} ${req.url}`);
    next();
  });
}

// Healthcheck (uzywany takze przez HEALTHCHECK w obrazie Dockera).
app.get('/api/health', (_req, res) =>
  res.json({
    ok: true,
    service: 'board360-mock',
    time: nowIso(),
    counts: {
      users: db.users.length,
      clients: db.clients.length,
      deals: db.deals.length,
      callLogs: db.callLogs.length,
      voiceReports: db.voiceReports.length,
    },
  }),
);

app.use('/api', require('./src/routes/auth'));
app.use('/api', require('./src/routes/clients'));
app.use('/api', require('./src/routes/deals'));
app.use('/api', require('./src/routes/intake'));
app.use('/api', require('./src/routes/catalog'));
app.use('/api', require('./src/routes/telephony'));
app.use('/api', require('./src/routes/tasks'));
app.use('/api', require('./src/routes/discussions'));
app.use('/api', require('./src/routes/service'));
app.use('/api', require('./src/routes/calendar'));

app.use((req, res) => res.status(404).json({ message: `Brak trasy: ${req.method} ${req.path}` }));

// Blad multera (za duzy plik) i wszystko inne — zawsze JSON, nigdy HTML,
// bo mobilka parsuje cialo bledu jako JSON.
app.use((err, _req, res, _next) => {
  console.error('Blad:', err.message);
  const status = err.status || (err.code === 'LIMIT_FILE_SIZE' ? 413 : 500);
  res.status(status).json({ message: err.message || 'Blad serwera.' });
});

const server = app.listen(PORT, HOST, () => {
  console.log(`board360-mock nasluchuje na ${HOST}:${PORT}`);
  console.log('Konta testowe:');
  console.log('  serwisant@ekotak.pl / test1234     (rola serwisant, bez deal.manage)');
  console.log('  koordynator@ekotak.pl / test1234   (rola koordynator, z deal.manage)');
  console.log('  admin@ekotak.pl / admin1234        (rola admin, pelne uprawnienia)');
  console.log(`Seed: ${db.clients.length} klientow, ${db.deals.length} deali, ${db.categories.length} kategorii.`);
  console.log(`Blokady etapow: ${STAGE_GATES ? 'wlaczone' : 'wylaczone'} (STAGE_GATES).`);
  if (SESSION_SECRET === 'dev-insecure-secret-change-me') {
    console.warn('UWAGA: domyslny SESSION_SECRET — ustaw wlasny, jesli mock jest widoczny w sieci.');
  }
});

// Docker wysyla SIGTERM przy `docker compose down` — bez tego kontener czeka
// 10 s na SIGKILL i restart trwa dluzej niz powinien.
for (const signal of ['SIGTERM', 'SIGINT']) {
  process.on(signal, () => {
    console.log(`${signal} — zamykam serwer.`);
    server.close(() => process.exit(0));
  });
}
