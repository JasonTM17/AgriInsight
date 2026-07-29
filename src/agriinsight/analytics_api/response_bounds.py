from __future__ import annotations

import json
from typing import Any

from fastapi.encoders import jsonable_encoder

from agriinsight.analytics_api.errors import ApiProblem

MAX_SERIALIZED_RESPONSE_BYTES = 1024 * 1024


def require_serialized_response_within_limit(payload: Any) -> None:
    """Fail closed before an inventory response can exceed the BFF ceiling."""

    encoded = json.dumps(
        jsonable_encoder(payload, by_alias=True, exclude_none=False),
        allow_nan=False,
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")
    if len(encoded) > MAX_SERIALIZED_RESPONSE_BYTES:
        raise ApiProblem(
            503,
            "analytics_response_too_large",
            "The analytics response exceeds the safe size limit.",
        )
