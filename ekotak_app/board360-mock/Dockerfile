# board360-mock — obraz testowego backendu TeamTalk.
#
# Jeden etap: brak kroku build (czysty CommonJS), wiec obraz to node:alpine +
# express + multer. Kontener chodzi jako uzytkownik `node`, nie root.

FROM node:22-alpine

ENV NODE_ENV=production \
    PORT=3001 \
    HOST=0.0.0.0 \
    UPLOADS_DIR=/app/uploads

WORKDIR /app

# Zaleznosci osobna warstwa — zmiana kodu nie unieważnia cache npm.
# `npm ci` gdy jest lockfile, `npm install` gdy jeszcze go nie ma.
COPY package.json package-lock.json* ./
RUN if [ -f package-lock.json ]; then npm ci --omit=dev; else npm install --omit=dev; fi \
    && npm cache clean --force

COPY server.js ./
COPY src ./src

RUN mkdir -p "$UPLOADS_DIR" && chown -R node:node /app
USER node

EXPOSE 3001

# Node ma globalny fetch (18+), wiec healthcheck nie potrzebuje curla w obrazie.
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD node -e "fetch('http://127.0.0.1:'+(process.env.PORT||3001)+'/api/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "server.js"]
