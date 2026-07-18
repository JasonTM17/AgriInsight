from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from datetime import date
from typing import Mapping

import pandas as pd


MAX_DETAIL_ROWS = 25_000
MAX_BUNDLE_BYTES = 10 * 1024 * 1024
MAX_TOP_N = 30
REPORT_VERSION = "cost-report-v1"
ALLOWED_REQUEST_KEYS = frozenset(
    {"scope", "farm", "crop", "activity", "supplier", "month_from", "month_to", "top_n"}
)
ALLOWED_SCOPES = frozenset({"all", "operating", "procurement"})
_MONTH_PATTERN = re.compile(r"^\d{4}-(0[1-9]|1[0-2])$")
_UNSAFE_VALUE_PARTS = ("/", "\\", "..", ":")


class ReportValidationError(ValueError):
    """Raised when an export request or its result violates the public contract."""


class ExportUnavailable(RuntimeError):
    """Raised when an optional export runtime is not explicitly provisioned."""


def _domain_values(frame: pd.DataFrame, column: str) -> frozenset[str]:
    if column not in frame.columns:
        raise ReportValidationError(f"Gold contract is missing required column: {column}")
    return frozenset(str(value) for value in frame[column].dropna().unique())


@dataclass(frozen=True, slots=True)
class CostReportDomains:
    farms: frozenset[str]
    crops: frozenset[str]
    activities: frozenset[str]
    suppliers: frozenset[str]
    months: frozenset[str]

    @classmethod
    def from_gold(cls, gold: Mapping[str, pd.DataFrame]) -> CostReportDomains:
        try:
            cost = gold["cost_activity_detail"]
            procurement = gold["procurement_detail"]
        except KeyError as error:
            raise ReportValidationError(f"Missing Gold dataset: {error.args[0]}") from error
        return cls(
            farms=_domain_values(cost, "farm_code")
            | _domain_values(procurement, "farm_code"),
            crops=_domain_values(cost, "crop_code"),
            activities=_domain_values(cost, "activity_type"),
            suppliers=_domain_values(procurement, "supplier_code"),
            months=_domain_values(cost, "month") | _domain_values(procurement, "month"),
        )


def _optional_domain_value(
    raw: Mapping[str, object], key: str, domain: frozenset[str]
) -> str | None:
    if key not in raw:
        return None
    value = raw[key]
    if not isinstance(value, str) or not value.strip():
        raise ReportValidationError(f"{key} must be a non-empty string")
    normalized = value.strip()
    if any(part in normalized for part in _UNSAFE_VALUE_PARTS):
        raise ReportValidationError(f"{key} contains a path-like value")
    if normalized not in domain:
        raise ReportValidationError(f"Unknown {key}: {normalized}")
    return normalized


def _optional_month(
    raw: Mapping[str, object], key: str, months: frozenset[str]
) -> str | None:
    if key not in raw:
        return None
    value = raw[key]
    if not isinstance(value, str) or not _MONTH_PATTERN.fullmatch(value):
        raise ReportValidationError(f"{key} must use YYYY-MM")
    if value not in months:
        raise ReportValidationError(f"Unknown {key}: {value}")
    return value


@dataclass(frozen=True, slots=True)
class CostReportRequest:
    scope: str = "all"
    farm: str | None = None
    crop: str | None = None
    activity: str | None = None
    supplier: str | None = None
    month_from: str | None = None
    month_to: str | None = None
    top_n: int = 15

    @classmethod
    def from_mapping(
        cls, raw: Mapping[str, object], domains: CostReportDomains
    ) -> CostReportRequest:
        if any(not isinstance(key, str) for key in raw):
            raise ReportValidationError("Report filter keys must be strings")
        unknown = set(raw) - ALLOWED_REQUEST_KEYS
        if unknown:
            raise ReportValidationError(
                f"Unknown report filter keys: {', '.join(sorted(unknown))}"
            )
        scope = raw.get("scope", "all")
        if not isinstance(scope, str) or scope not in ALLOWED_SCOPES:
            raise ReportValidationError("scope must be all, operating, or procurement")
        top_n = raw.get("top_n", 15)
        if isinstance(top_n, bool) or not isinstance(top_n, int) or not 1 <= top_n <= MAX_TOP_N:
            raise ReportValidationError(f"top_n must be an integer from 1 to {MAX_TOP_N}")

        request = cls(
            scope=scope,
            farm=_optional_domain_value(raw, "farm", domains.farms),
            crop=_optional_domain_value(raw, "crop", domains.crops),
            activity=_optional_domain_value(raw, "activity", domains.activities),
            supplier=_optional_domain_value(raw, "supplier", domains.suppliers),
            month_from=_optional_month(raw, "month_from", domains.months),
            month_to=_optional_month(raw, "month_to", domains.months),
            top_n=top_n,
        )
        request._validate_scope_fields()
        if request.month_from and request.month_to and request.month_from > request.month_to:
            raise ReportValidationError("month_from must not be after month_to")
        return request

    def _validate_scope_fields(self) -> None:
        if self.scope == "all" and (self.crop or self.activity or self.supplier):
            raise ReportValidationError(
                "scope=all accepts only farm and month filters; select a single lens for other filters"
            )
        if self.scope == "operating" and self.supplier:
            raise ReportValidationError("supplier is only valid for procurement scope")
        if self.scope == "procurement" and (self.crop or self.activity):
            raise ReportValidationError("crop and activity are only valid for operating scope")

    def canonical_dict(self) -> dict[str, object]:
        return {
            "scope": self.scope,
            "farm": self.farm,
            "crop": self.crop,
            "activity": self.activity,
            "supplier": self.supplier,
            "month_from": self.month_from,
            "month_to": self.month_to,
            "top_n": self.top_n,
        }

    @property
    def filter_hash(self) -> str:
        payload = json.dumps(
            self.canonical_dict(), ensure_ascii=True, sort_keys=True, separators=(",", ":")
        )
        return hashlib.sha256(payload.encode("ascii")).hexdigest()[:12]


@dataclass(frozen=True, slots=True)
class CostReportMetadata:
    run_id: str
    as_of_date: str
    source_pipeline: str
    filter_hash: str
    export_version: str = REPORT_VERSION

    @classmethod
    def from_manifest(
        cls, manifest: Mapping[str, object], request: CostReportRequest
    ) -> CostReportMetadata:
        run_id = manifest.get("run_id")
        as_of_date = manifest.get("as_of_date")
        source_pipeline = manifest.get("pipeline")
        if not isinstance(run_id, str) or not run_id:
            raise ReportValidationError("Manifest run_id is required")
        if not isinstance(source_pipeline, str) or not source_pipeline:
            raise ReportValidationError("Manifest pipeline is required")
        if not isinstance(as_of_date, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", as_of_date):
            raise ReportValidationError("Manifest as_of_date must use YYYY-MM-DD")
        try:
            date.fromisoformat(as_of_date)
        except ValueError as error:
            raise ReportValidationError("Manifest as_of_date must be a valid date") from error
        return cls(
            run_id=run_id,
            as_of_date=as_of_date,
            source_pipeline=source_pipeline,
            filter_hash=request.filter_hash,
        )

    def filename(self, request: CostReportRequest, extension: str) -> str:
        if extension not in {"csv", "pdf", "xlsx"}:
            raise ReportValidationError(f"Unsupported report extension: {extension}")
        return f"cost-analysis_{self.as_of_date}_{request.scope}_{self.filter_hash}.{extension}"


@dataclass(frozen=True, slots=True)
class PreparedCostReport:
    summary: pd.DataFrame
    monthly: pd.DataFrame
    cost_detail: pd.DataFrame
    procurement_detail: pd.DataFrame
    checks: pd.DataFrame
    metadata: pd.DataFrame
    csv_ledger: pd.DataFrame

    @property
    def detail_row_count(self) -> int:
        return len(self.cost_detail) + len(self.procurement_detail)


@dataclass(frozen=True, slots=True)
class ReportArtifact:
    filename: str
    mime_type: str
    content: bytes


@dataclass(frozen=True, slots=True)
class CostReportBundle:
    request: CostReportRequest
    metadata: CostReportMetadata
    report: PreparedCostReport
    csv: ReportArtifact
    pdf: ReportArtifact
    xlsx: ReportArtifact | None
    xlsx_unavailable_reason: str | None = None
