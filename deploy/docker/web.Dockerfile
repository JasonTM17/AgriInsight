# syntax=docker/dockerfile:1.7

FROM node:24.12.0-bookworm-slim@sha256:6d8047885b91084ceff824c02950be237dafcbfd3d1b6e69d49c919868e806be AS dependencies

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

FROM node:24.12.0-bookworm-slim@sha256:6d8047885b91084ceff824c02950be237dafcbfd3d1b6e69d49c919868e806be AS runtime

ARG OCI_SOURCE="https://github.com/JasonTM17/AgriInsight"
ARG OCI_REVISION="unknown"
ARG OCI_VERSION="0.1.0"

LABEL org.opencontainers.image.title="AgriInsight Web" \
      org.opencontainers.image.description="Tenant-safe Next.js agriculture analytics and operations platform" \
      org.opencontainers.image.source=$OCI_SOURCE \
      org.opencontainers.image.revision=$OCI_REVISION \
      org.opencontainers.image.version=$OCI_VERSION \
      org.opencontainers.image.licenses="MIT"

ENV HOSTNAME=0.0.0.0 \
    HOME=/tmp \
    NEXT_TELEMETRY_DISABLED=1 \
    NODE_ENV=production \
    PORT=3100

RUN groupadd --gid 10001 agriinsight \
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
