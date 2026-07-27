from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from agriinsight.analytics_api.assistant_corpus import build_evidence_corpus
from agriinsight.analytics_api.assistant_models import AssistantAnswer, AssistantQuery
from agriinsight.analytics_api.deepseek_assistant_client import (
    AssistantProviderError,
)
from agriinsight.analytics_api.dependencies import (
    AssistantAuthorization,
    assistant_authorization,
)
from agriinsight.analytics_api.errors import ApiProblem, correlation_id
from agriinsight.analytics_api.routers.common import (
    assert_snapshot_current,
    verified_snapshot,
)

router = APIRouter(tags=["analytics-assistant"])


@router.post(
    "/assistant/query",
    operation_id="queryAnalyticsAssistant",
    response_model=AssistantAnswer,
)
async def query_assistant(
    query: AssistantQuery,
    request: Request,
    authorization: AssistantAuthorization = Depends(assistant_authorization),
) -> AssistantAnswer:
    snapshot = verified_snapshot(request, authorization.scope)
    corpus = build_evidence_corpus(
        snapshot,
        authorization.scope,
        sources=authorization.sources,
    )
    try:
        answer = await request.app.state.assistant_service.answer(
            query,
            authorization.scope,
            corpus,
            correlation_id(request),
        )
    except AssistantProviderError as error:
        raise _provider_problem(error) from error
    assert_snapshot_current(request, snapshot)
    return answer


def _provider_problem(error: AssistantProviderError) -> ApiProblem:
    status_code = (
        502
        if error.code
        in {
            "assistant_provider_invalid_response",
            "assistant_provider_rejected_request",
        }
        else 503
    )
    return ApiProblem(
        status_code,
        error.code,
        "The agricultural assistant is temporarily unavailable.",
    )
