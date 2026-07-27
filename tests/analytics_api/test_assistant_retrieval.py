from __future__ import annotations

from datetime import date
from uuid import UUID

from agriinsight.analytics_api.assistant_retrieval import (
    EvidenceChunk,
    EvidenceRetriever,
)
from agriinsight.analytics_api.auth_scope import AuthorizedScope


TENANT_A = UUID("20000000-0000-4000-8000-000000000001")
TENANT_B = UUID("20000000-0000-4000-8000-000000000002")


def _scope(
    *,
    tenant_id: UUID = TENANT_A,
    farms: frozenset[str] = frozenset({"FARM-01"}),
    farm_tenant_wide: bool = False,
) -> AuthorizedScope:
    return AuthorizedScope(
        farm_codes=farms,
        farm_tenant_wide=farm_tenant_wide,
        tenant_id=tenant_id,
        warehouse_codes=frozenset(),
        warehouse_tenant_wide=False,
    )


def _chunk(
    evidence_id: str,
    content: str,
    *,
    tenant_id: UUID = TENANT_A,
    farm_codes: frozenset[str] = frozenset({"FARM-01"}),
    tenant_wide_only: bool = False,
) -> EvidenceChunk:
    return EvidenceChunk(
        evidence_id=evidence_id,
        title=evidence_id.replace("-", " "),
        content=content,
        source_type="farm-performance",
        as_of=date(2026, 7, 27),
        tenant_id=tenant_id,
        farm_codes=farm_codes,
        warehouse_codes=frozenset(),
        tenant_wide_only=tenant_wide_only,
    )


def test_scope_filter_runs_before_ranking() -> None:
    corpus = [
        _chunk("ev-allowed", "FARM-01 có chi phí vận hành 120 triệu đồng."),
        _chunk(
            "ev-other-farm",
            "FARM-02 có chi phí vận hành 999 triệu đồng.",
            farm_codes=frozenset({"FARM-02"}),
        ),
        _chunk(
            "ev-other-tenant",
            "FARM-01 có chi phí vận hành bí mật.",
            tenant_id=TENANT_B,
        ),
        _chunk(
            "ev-global",
            "Tổng chi phí toàn doanh nghiệp.",
            farm_codes=frozenset(),
            tenant_wide_only=True,
        ),
    ]

    result = EvidenceRetriever(max_items=8, max_characters=12_000).retrieve(
        "Chi phí FARM-01 là bao nhiêu?", _scope(), corpus
    )

    assert [item.chunk.evidence_id for item in result] == ["ev-allowed"]


def test_tenant_wide_scope_may_retrieve_global_evidence() -> None:
    result = EvidenceRetriever(max_items=8, max_characters=12_000).retrieve(
        "Tổng chi phí doanh nghiệp",
        _scope(farm_tenant_wide=True),
        [
            _chunk(
                "ev-global",
                "Tổng chi phí toàn doanh nghiệp là 3 tỷ đồng.",
                farm_codes=frozenset(),
                tenant_wide_only=True,
            )
        ],
    )

    assert [item.chunk.evidence_id for item in result] == ["ev-global"]


def test_vietnamese_terms_codes_and_character_budget_rank_deterministically() -> None:
    retriever = EvidenceRetriever(max_items=2, max_characters=120)
    corpus = [
        _chunk(
            "ev-cost-02",
            "FARM-01 chi phí phân bón 90 triệu đồng và đang vượt ngân sách.",
        ),
        _chunk(
            "ev-health",
            "FARM-01 có rủi ro sâu bệnh mức thấp.",
        ),
        _chunk(
            "ev-cost-01",
            "FARM-01 chi phí vận hành 120 triệu đồng.",
        ),
    ]

    first = retriever.retrieve("chi phí trang trại FARM-01", _scope(), corpus)
    second = retriever.retrieve("chi phí trang trại FARM-01", _scope(), corpus)

    assert [item.chunk.evidence_id for item in first] == [
        "ev-cost-01",
        "ev-cost-02",
    ]
    assert first == second
    assert sum(len(item.chunk.content) for item in first) <= 120


def test_unrelated_question_returns_no_evidence() -> None:
    result = EvidenceRetriever(max_items=8, max_characters=12_000).retrieve(
        "Ai vô địch bóng đá?", _scope(), [_chunk("ev-cost", "Chi phí FARM-01")]
    )

    assert result == []


def test_punctuation_and_emoji_only_questions_return_no_evidence() -> None:
    retriever = EvidenceRetriever(max_items=8, max_characters=12_000)
    corpus = [_chunk("ev-cost", "Chi phí FARM-01")]

    assert retriever.retrieve("???", _scope(), corpus) == []
    assert retriever.retrieve("🌾 🌱", _scope(), corpus) == []
