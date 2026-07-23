from __future__ import annotations

import re
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHttpException

from agriinsight.analytics_api.models import ErrorDetail, ErrorEnvelope

CORRELATION_HEADER = "X-Correlation-Id"
_SAFE_CORRELATION = re.compile(r"^[A-Za-z0-9._-]{8,64}$")


class ApiProblem(RuntimeError):
    def __init__(self, status_code: int, code: str, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.safe_message = message


def correlation_id(request: Request) -> str:
    return str(getattr(request.state, "correlation_id", "unavailable"))


def install_error_boundary(app: FastAPI) -> None:
    @app.middleware("http")
    async def correlation_boundary(request: Request, call_next):
        supplied = request.headers.get(CORRELATION_HEADER, "")
        request.state.correlation_id = (
            supplied if _SAFE_CORRELATION.fullmatch(supplied) else str(uuid4())
        )
        response = await call_next(request)
        response.headers[CORRELATION_HEADER] = request.state.correlation_id
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    @app.exception_handler(ApiProblem)
    async def api_problem_handler(
        request: Request, error: ApiProblem
    ) -> JSONResponse:
        return _response(
            request,
            error.status_code,
            error.code,
            error.safe_message,
        )

    @app.exception_handler(RequestValidationError)
    async def validation_handler(
        request: Request, _error: RequestValidationError
    ) -> JSONResponse:
        return _response(
            request,
            422,
            "invalid_request",
            "Request parameters are invalid.",
        )

    @app.exception_handler(StarletteHttpException)
    async def http_error_handler(
        request: Request, error: StarletteHttpException
    ) -> JSONResponse:
        code = {
            404: "route_not_found",
            405: "method_not_allowed",
        }.get(error.status_code, "http_error")
        return _response(
            request,
            error.status_code,
            code,
            "The requested analytics operation is unavailable.",
        )

    @app.exception_handler(Exception)
    async def unexpected_handler(
        request: Request, _error: Exception
    ) -> JSONResponse:
        return _response(
            request,
            500,
            "internal_error",
            "The analytics request could not be completed.",
        )


def _response(
    request: Request,
    status_code: int,
    code: str,
    message: str,
) -> JSONResponse:
    payload = ErrorEnvelope(
        correlation_id=correlation_id(request),
        error=ErrorDetail(code=code, message=message),
    )
    return JSONResponse(
        status_code=status_code,
        content=payload.model_dump(by_alias=True),
        headers={
            CORRELATION_HEADER: correlation_id(request),
            "Cache-Control": "no-store",
            "X-Content-Type-Options": "nosniff",
        },
    )
