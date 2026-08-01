from __future__ import annotations

import json
from datetime import date
from pathlib import Path
from uuid import UUID

from agriinsight.analytics_api.assistant_retrieval import (
    EvidenceChunk,
    EvidenceRetriever,
)
from agriinsight.analytics_api.auth_scope import AuthorizedScope


FIXTURE = (
    Path(__file__).parents[1]
    / "fixtures"
    / "assistant-retrieval-evaluation-v1.json"
)
TENANT_ID = UUID("20000000-0000-4000-8000-000000000001")


def test_retrieval_evaluation_release_gates() -> None:
    payload = json.loads(FIXTURE.read_text(encoding="utf-8"))
    assert payload["version"] == "1.1.0"
    corpus = [_chunk(item) for item in payload["corpus"]]
    retriever = EvidenceRetriever(max_items=5, max_characters=12_000)
    scope = AuthorizedScope(
        farm_codes=frozenset({"FARM-01", "FARM-02"}),
        farm_tenant_wide=True,
        tenant_id=TENANT_ID,
        warehouse_codes=frozenset({"WH-01"}),
        warehouse_tenant_wide=True,
    )

    expected_count = 0
    retrieved_expected_count = 0
    refusal_predictions = 0
    refusal_true_positives = 0
    cross_scope_leaks = 0
    for case in payload["cases"]:
        result = retriever.retrieve(case["question"], scope, corpus)
        identifiers = {item.chunk.evidence_id for item in result}
        expected = set(case["expectedEvidenceIds"])
        expected_count += len(expected)
        retrieved_expected_count += len(identifiers & expected)
        if not result:
            refusal_predictions += 1
            if not case["shouldRetrieve"]:
                refusal_true_positives += 1
        cross_scope_leaks += sum(
            item.chunk.tenant_id != TENANT_ID
            or (
                bool(item.chunk.farm_codes)
                and not item.chunk.farm_codes.issubset(scope.farm_codes)
            )
            or (
                bool(item.chunk.warehouse_codes)
                and not item.chunk.warehouse_codes.issubset(
                    scope.warehouse_codes
                )
            )
            for item in result
        )

    recall_at_five = retrieved_expected_count / expected_count
    refusal_precision = refusal_true_positives / refusal_predictions
    assert recall_at_five == 1.0
    assert refusal_precision == 1.0
    assert recall_at_five >= 0.90
    assert refusal_precision >= 0.95
    assert cross_scope_leaks == 0


def _chunk(item: dict) -> EvidenceChunk:
    return EvidenceChunk(
        evidence_id=item["evidenceId"],
        title=item["title"],
        content=item["content"],
        source_type=item["sourceType"],
        as_of=date.fromisoformat(item["asOf"]),
        tenant_id=UUID(item["tenantId"]),
        farm_codes=frozenset(item["farmCodes"]),
        warehouse_codes=frozenset(item["warehouseCodes"]),
        tenant_wide_only=item["tenantWideOnly"],
    )
