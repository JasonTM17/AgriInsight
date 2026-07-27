from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from datetime import date
from uuid import UUID

from agriinsight.analytics_api.assistant_models import EvidenceSource
from agriinsight.analytics_api.auth_scope import AuthorizedScope


_TOKEN = re.compile(r"[a-z0-9]+")
_TERM_EXPANSIONS = {
    "chi phi": {"cost", "budget", "spend"},
    "gia von": {"cost", "spend"},
    "kho": {"inventory", "warehouse", "stock"},
    "loi nhuan": {"profit", "margin", "revenue"},
    "nang suat": {"yield", "harvest"},
    "sau benh": {"pest", "disease", "risk", "health"},
    "trang trai": {"farm"},
}


@dataclass(frozen=True, slots=True)
class EvidenceChunk:
    evidence_id: str
    title: str
    content: str
    source_type: EvidenceSource
    as_of: date
    tenant_id: UUID
    farm_codes: frozenset[str]
    warehouse_codes: frozenset[str]
    tenant_wide_only: bool = False

    def __post_init__(self) -> None:
        if not re.fullmatch(r"[a-z0-9][a-z0-9._:-]{0,127}", self.evidence_id):
            raise ValueError("evidence_id has an invalid format")
        if not self.title.strip() or len(self.title) > 200:
            raise ValueError("evidence title has an invalid length")
        if not self.content.strip() or len(self.content) > 4_000:
            raise ValueError("evidence content has an invalid length")
        if self.tenant_wide_only and (
            self.farm_codes or self.warehouse_codes
        ):
            raise ValueError(
                "tenant-wide evidence cannot also declare resource codes"
            )


@dataclass(frozen=True, slots=True)
class RetrievedEvidence:
    chunk: EvidenceChunk
    score: int


class EvidenceRetriever:
    def __init__(self, *, max_items: int, max_characters: int) -> None:
        if not 1 <= max_items <= 20:
            raise ValueError("max_items must be between 1 and 20")
        if not 100 <= max_characters <= 50_000:
            raise ValueError("max_characters must be between 100 and 50000")
        self._max_items = max_items
        self._max_characters = max_characters

    def retrieve(
        self,
        question: str,
        scope: AuthorizedScope,
        corpus: list[EvidenceChunk],
    ) -> list[RetrievedEvidence]:
        normalized_query = _normalize(question)
        query_tokens = _expanded_tokens(normalized_query)
        candidates: list[RetrievedEvidence] = []
        for chunk in corpus:
            if not _is_visible(chunk, scope):
                continue
            score = _score(
                normalized_query,
                query_tokens,
                chunk,
            )
            if score > 0:
                candidates.append(RetrievedEvidence(chunk=chunk, score=score))
        candidates.sort(key=lambda item: (-item.score, item.chunk.evidence_id))

        selected: list[RetrievedEvidence] = []
        used_characters = 0
        for candidate in candidates:
            content_length = len(candidate.chunk.content)
            if used_characters + content_length > self._max_characters:
                continue
            selected.append(candidate)
            used_characters += content_length
            if len(selected) == self._max_items:
                break
        return selected


def _is_visible(chunk: EvidenceChunk, scope: AuthorizedScope) -> bool:
    if chunk.tenant_id != scope.tenant_id:
        return False
    if chunk.tenant_wide_only and not scope.tenant_wide:
        return False
    if chunk.farm_codes and not (
        scope.farm_tenant_wide or chunk.farm_codes.issubset(scope.farm_codes)
    ):
        return False
    if chunk.warehouse_codes and not (
        scope.warehouse_tenant_wide
        or chunk.warehouse_codes.issubset(scope.warehouse_codes)
    ):
        return False
    return True


def _score(
    normalized_query: str,
    query_tokens: frozenset[str],
    chunk: EvidenceChunk,
) -> int:
    normalized_title = _normalize(chunk.title)
    normalized_content = _normalize(chunk.content)
    title_tokens = frozenset(_TOKEN.findall(normalized_title))
    content_tokens = frozenset(_TOKEN.findall(normalized_content))

    score = 0
    if normalized_query in normalized_title:
        score += 10_000
    elif normalized_query in normalized_content:
        score += 6_000
    score += len(query_tokens & title_tokens) * 500
    score += len(query_tokens & content_tokens) * 100

    resource_codes = chunk.farm_codes | chunk.warehouse_codes
    for code in resource_codes:
        if _normalize(code) in normalized_query:
            score += 5_000
    return score


def _expanded_tokens(normalized_query: str) -> frozenset[str]:
    tokens = set(_TOKEN.findall(normalized_query))
    for phrase, expansions in _TERM_EXPANSIONS.items():
        if phrase in normalized_query:
            tokens.update(expansions)
    return frozenset(tokens)


def _normalize(value: str) -> str:
    decomposed = unicodedata.normalize("NFKD", value.casefold())
    without_marks = "".join(
        character
        for character in decomposed
        if unicodedata.category(character) != "Mn"
    )
    return " ".join(_TOKEN.findall(without_marks))
