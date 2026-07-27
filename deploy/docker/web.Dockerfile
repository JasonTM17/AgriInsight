# syntax=docker/dockerfile:1.7

FROM node:24.18.0-bookworm-slim@sha256:6f7b03f7c2c8e2e784dcf9295400527b9b1270fd37b7e9a7285cf83b6951452d AS dependencies

ENV NEXT_TELEMETRY_DISABLED=1
WORKDIR /workspace/web
COPY web/package.json web/package-lock.json ./
RUN npm ci --ignore-scripts

FROM dependencies AS build

WORKDIR /workspace
COPY web ./web
COPY dashboard/assets/generated ./dashboard/assets/generated
WORKDIR /workspace/web
RUN npm run build

FROM node:24.18.0-bookworm-slim@sha256:6f7b03f7c2c8e2e784dcf9295400527b9b1270fd37b7e9a7285cf83b6951452d AS runtime

ARG OCI_SOURCE="https://github.com/JasonTM17/AgriInsight"
ARG OCI_REVISION="unknown"
ARG OCI_VERSION="0.1.0"

LABEL org.opencontainers.image.title="AgriInsight Web" \
      org.opencontainers.image.description="Tenant-safe Next.js agriculture analytics and operations platform" \
      org.opencontainers.image.source=$OCI_SOURCE \
      org.opencontainers.image.revision=$OCI_REVISION \
      org.opencontainers.image.version=$OCI_VERSION

ENV HOSTNAME=0.0.0.0 \
    HOME=/tmp \
    NEXT_TELEMETRY_DISABLED=1 \
    NODE_ENV=production \
    PORT=3100

RUN rm -rf /usr/local/lib/node_modules/npm \
        /usr/local/lib/node_modules/corepack \
        /opt/yarn-v* \
    && rm -f /usr/local/bin/npm \
        /usr/local/bin/npx \
        /usr/local/bin/corepack \
        /usr/local/bin/yarn \
        /usr/local/bin/yarnpkg \
    && groupadd --gid 10001 agriinsight \
    && useradd --uid 10001 --gid 10001 --home-dir /app --no-create-home --shell /usr/sbin/nologin agriinsight

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/web/.next/standalone ./
COPY --from=build --chown=10001:10001 /workspace/web/.next/static ./web/.next/static
COPY --from=build --chown=10001:10001 /workspace/web/public ./web/public
COPY --from=build --chown=10001:10001 /workspace/web/db ./web/db

USER 10001:10001

EXPOSE 3100
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
  CMD ["node", "-e", "require('node:http').get('http://127.0.0.1:3100/api/health/live',response=>{process.exit(response.statusCode===200?0:1)}).on('error',()=>process.exit(1))"]
CMD ["node", "web/server.js"]
