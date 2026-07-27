from __future__ import annotations

import re
from datetime import date
from typing import Literal

from pydantic import Field, field_validator, model_validator
from typing_extensions import Self

from agriinsight.analytics_api.models import ApiModel


_HTML_TAG = re.compile(r"<\s*/?\s*[A-Za-z][^>]*>")
_CONTROL_CHARACTER = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")
EvidenceSource = Literal[
    "overview",
    "farm-performance",
    "inventory",
    "crop-health",
    "data-quality",
    "cost",
    "procurement",
]


class ConversationTurn(ApiModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=2_000)

    @field_validator("content")
    @classmethod
    def validate_content(cls, value: str) -> str:
        return _plain_text(value, "conversation content")


class AssistantQuery(ApiModel):
    question: str = Field(min_length=1, max_length=1_200)
    history: list[ConversationTurn] = Field(default_factory=list, max_length=6)

    @field_validator("question")
    @classmethod
    def validate_question(cls, value: str) -> str:
        return _plain_text(value, "question")


class EvidenceCitation(ApiModel):
    evidence_id: str = Field(
        min_length=1,
        max_length=128,
        pattern=r"^[a-z0-9][a-z0-9._:-]*$",
    )
    title: str = Field(min_length=1, max_length=200)
    excerpt: str = Field(min_length=1, max_length=1_200)
    source_type: EvidenceSource
    as_of: date

    @field_validator("title", "excerpt")
    @classmethod
    def validate_evidence_text(cls, value: str) -> str:
        return _plain_text(value, "evidence text")


class AssistantUsage(ApiModel):
    prompt_tokens: int = Field(ge=0)
    completion_tokens: int = Field(ge=0)
    total_tokens: int = Field(ge=0)
    prompt_cache_hit_tokens: int = Field(ge=0)
    prompt_cache_miss_tokens: int = Field(ge=0)

    @model_validator(mode="after")
    def validate_totals(self) -> Self:
        if (
            self.prompt_cache_hit_tokens + self.prompt_cache_miss_tokens
            != self.prompt_tokens
        ):
            raise ValueError("Prompt cache token counts must equal prompt tokens")
        if self.prompt_tokens + self.completion_tokens != self.total_tokens:
            raise ValueError("Prompt and completion tokens must equal total tokens")
        return self


class AssistantAnswer(ApiModel):
    status: Literal["answered", "insufficient_evidence"]
    answer: str = Field(min_length=1, max_length=8_000)
    citations: list[EvidenceCitation] = Field(default_factory=list, max_length=20)
    usage: AssistantUsage

    @field_validator("answer")
    @classmethod
    def validate_answer(cls, value: str) -> str:
        return _plain_text(value, "answer")

    @model_validator(mode="after")
    def validate_citation_requirement(self) -> Self:
        if self.status == "answered" and not self.citations:
            raise ValueError("Answered responses require at least one citation")
        return self


def _plain_text(value: str, label: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise ValueError(f"{label} must not be blank")
    if _CONTROL_CHARACTER.search(normalized):
        raise ValueError(f"{label} contains a control character")
    if _HTML_TAG.search(normalized):
        raise ValueError(f"{label} must be plain text")
    return normalized
