from __future__ import annotations

import os

import uvicorn


def main() -> None:
    host = os.environ.get("AGRIINSIGHT_ANALYTICS_BIND_HOST", "127.0.0.1")
    if host not in {"127.0.0.1", "0.0.0.0"}:
        raise ValueError("Analytics bind host must be loopback or all interfaces")
    port = int(os.environ.get("AGRIINSIGHT_ANALYTICS_PORT", "8081"))
    if not 1 <= port <= 65_535:
        raise ValueError("Analytics port is outside the valid range")
    uvicorn.run(
        "agriinsight.analytics_api.app:create_app",
        factory=True,
        host=host,
        port=port,
        server_header=False,
    )


if __name__ == "__main__":
    main()
