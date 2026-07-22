# syntax=docker/dockerfile:1.7

FROM python:3.13-slim@sha256:6771159cd4fa5d9bba1258caf0b82e6b73458c694d178ad97c5e925c2d0e1a91

ARG OCI_SOURCE="https://github.com/JasonTM17/AgriInsight"
ARG OCI_REVISION="unknown"
ARG OCI_VERSION="0.2.0"

LABEL org.opencontainers.image.title="AgriInsight Analytics" \
      org.opencontainers.image.description="Bronze-Silver-Gold agriculture analytics pipeline and dashboard" \
      org.opencontainers.image.source=$OCI_SOURCE \
      org.opencontainers.image.revision=$OCI_REVISION \
      org.opencontainers.image.version=$OCI_VERSION \
      org.opencontainers.image.licenses="MIT"

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

COPY pyproject.toml README.md ./
COPY src ./src
COPY dashboard ./dashboard

RUN python -m pip install --no-cache-dir ".[dashboard,reports]"

RUN python -m pip check

RUN useradd --create-home --uid 10001 --shell /usr/sbin/nologin agriinsight \
    && mkdir -p /app/artifacts \
    && chown -R agriinsight:agriinsight /app

USER agriinsight

STOPSIGNAL SIGTERM

ENTRYPOINT ["python", "-m", "agriinsight"]
CMD ["run", "--output", "/app/artifacts"]
