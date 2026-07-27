# syntax=docker/dockerfile:1.7

FROM python:3.13-slim@sha256:6771159cd4fa5d9bba1258caf0b82e6b73458c694d178ad97c5e925c2d0e1a91 AS build

ENV PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1
WORKDIR /workspace
COPY pyproject.toml README.md ./
COPY src ./src
RUN python -m venv /opt/venv \
    && /opt/venv/bin/python -m pip install --upgrade pip \
    && /opt/venv/bin/python -m pip install ".[api]" \
    && /opt/venv/bin/python -m pip check

FROM python:3.13-slim@sha256:6771159cd4fa5d9bba1258caf0b82e6b73458c694d178ad97c5e925c2d0e1a91 AS runtime

ARG OCI_SOURCE="https://github.com/JasonTM17/AgriInsight"
ARG OCI_REVISION="unknown"
ARG OCI_VERSION="0.2.0"

LABEL org.opencontainers.image.title="AgriInsight Analytics API" \
      org.opencontainers.image.description="FastAPI service over verified agriculture snapshots with optional scoped RAG" \
      org.opencontainers.image.source=$OCI_SOURCE \
      org.opencontainers.image.revision=$OCI_REVISION \
      org.opencontainers.image.version=$OCI_VERSION

ENV AGRIINSIGHT_ANALYTICS_BIND_HOST=0.0.0.0 \
    AGRIINSIGHT_ANALYTICS_PORT=8081 \
    HOME=/tmp \
    PATH=/opt/venv/bin:$PATH \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN groupadd --gid 10001 agriinsight \
    && useradd --uid 10001 --gid 10001 --home-dir /app --no-create-home --shell /usr/sbin/nologin agriinsight

WORKDIR /app
COPY --from=build --chown=10001:10001 /opt/venv /opt/venv

USER 10001:10001

EXPOSE 8081
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
  CMD ["python", "-c", "import sys,urllib.request;sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8081/health/live',timeout=2).status==200 else 1)"]
ENTRYPOINT ["python", "-m", "agriinsight.analytics_api"]
