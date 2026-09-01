'use strict';
/*
 * Konfiguracja board360-mock. Wszystko z ENV, z bezpiecznymi domyslnymi
 * wartosciami dla `docker compose up` bez zadnego pliku .env.
 */

const path = require('path');

const bool = (value, fallback) => {
  if (value === undefined || value === '') return fallback;
  return ['1', 'true', 'yes', 'on'].includes(String(value).toLowerCase());
};

module.exports = {
  PORT: Number(process.env.PORT || 3001),
  HOST: process.env.HOST || '0.0.0.0',

  // Sekret podpisu tokenu sesji — ten sam mechanizm co board360 (cookie b360_session).
  SESSION_SECRET: process.env.SESSION_SECRET || 'dev-insecure-secret-change-me',
  SESSION_COOKIE: 'b360_session',
  MOBILE_SESSION_TTL: Number(process.env.MOBILE_SESSION_TTL || 60 * 60 * 24 * 30), // 30 dni

  UPLOADS_DIR: process.env.UPLOADS_DIR || path.join(__dirname, '..', 'uploads'),
  MAX_UPLOAD_BYTES: Number(process.env.MAX_UPLOAD_BYTES || 25 * 1024 * 1024),

  // Blokady walidacyjne przejsc etapu (422 + missing[]). Wylaczenie
  // (STAGE_GATES=0) przydaje sie, gdy testujemy sam przeplyw ekranow.
  STAGE_GATES: bool(process.env.STAGE_GATES, true),

  // Po wgraniu nagrania mock dopisuje udawana transkrypcje — board360 robi to
  // przez Whisper. MOCK_TRANSCRIPT=0 zostawia `transcript: null`.
  MOCK_TRANSCRIPT: bool(process.env.MOCK_TRANSCRIPT, true),

  LOG_REQUESTS: bool(process.env.LOG_REQUESTS, true),
};
