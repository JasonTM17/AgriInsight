FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

COPY pyproject.toml README.md ./
COPY src ./src
COPY dashboard ./dashboard

RUN python -m pip install --no-cache-dir ".[dashboard]"

RUN useradd --create-home --uid 10001 agriinsight \
    && mkdir -p /app/artifacts \
    && chown -R agriinsight:agriinsight /app

USER agriinsight

ENTRYPOINT ["python", "-m", "agriinsight"]
CMD ["run", "--output", "/app/artifacts"]

