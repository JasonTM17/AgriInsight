from __future__ import annotations

import re
from collections.abc import Iterable
from typing import Any

import pandas as pd

from agriinsight.analytics_api.assistant_models import EvidenceSource
from agriinsight.analytics_api.assistant_retrieval import EvidenceChunk
from agriinsight.analytics_api.auth_scope import AuthorizedScope
from agriinsight.analytics_snapshot import ArtifactSnapshot


_ALLOWED_SOURCES: frozenset[EvidenceSource] = frozenset(
    {"overview", "farm-performance", "inventory", "crop-health", "cost"}
)
_CONTROL_CHARACTER = re.compile(r"[\x00-\x1f\x7f]")


def build_evidence_corpus(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    *,
    sources: frozenset[EvidenceSource],
) -> list[EvidenceChunk]:
    unknown = sources - _ALLOWED_SOURCES
    if unknown:
        raise ValueError("unsupported assistant evidence source")
    as_of = pd.Timestamp(snapshot.manifest["as_of_date"]).date()
    chunks: list[EvidenceChunk] = []
    if "farm-performance" in sources:
        chunks.extend(_farm_performance(snapshot, scope, as_of))
    if "cost" in sources:
        chunks.extend(_farm_costs(snapshot, scope, as_of))
    if "crop-health" in sources:
        chunks.extend(_crop_health(snapshot, scope, as_of))
    if "inventory" in sources:
        chunks.extend(_inventory(snapshot, scope, as_of))
    if "overview" in sources:
        chunks.extend(_overview(snapshot, scope, as_of))
    chunks.sort(key=lambda chunk: chunk.evidence_id)
    identifiers = [chunk.evidence_id for chunk in chunks]
    if len(identifiers) != len(set(identifiers)):
        raise ValueError("assistant evidence IDs must be unique")
    return chunks


def _farm_performance(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    as_of,
) -> Iterable[EvidenceChunk]:
    for row in _records(snapshot.csv["farm_performance"], "farm_code"):
        farm_code = _text(row["farm_code"])
        if not _farm_allowed(scope, farm_code):
            continue
        farm_name = _text(row["farm_name"])
        yield EvidenceChunk(
            evidence_id=f"farm-performance:{_identifier(farm_code)}",
            title=f"Hiệu quả {farm_code} · {farm_name}",
            content=(
                f"Trang trại {farm_code} ({farm_name}) có diện tích canh tác "
                f"{_number(row['cultivated_area_ha'])} ha, sản lượng thu hoạch "
                f"{_number(row['harvest_quantity_kg'])} kg, năng suất "
                f"{_number(row['yield_kg_per_ha'])} kg/ha, doanh thu "
                f"{_number(row['total_revenue_vnd'])} VND, tổng chi phí "
                f"{_number(row['total_cost_vnd'])} VND, lợi nhuận "
                f"{_number(row['profit_vnd'])} VND và biên lợi nhuận "
                f"{_number(row['profit_margin_pct'])}%."
            ),
            source_type="farm-performance",
            as_of=as_of,
            tenant_id=scope.tenant_id,
            farm_codes=frozenset({farm_code}),
            warehouse_codes=frozenset(),
        )


def _farm_costs(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    as_of,
) -> Iterable[EvidenceChunk]:
    for row in _records(snapshot.csv["cost_farm"], "farm_code"):
        farm_code = _text(row["farm_code"])
        if not _farm_allowed(scope, farm_code):
            continue
        farm_name = _text(row["farm_name"])
        yield EvidenceChunk(
            evidence_id=f"cost:{_identifier(farm_code)}",
            title=f"Chi phí {farm_code} · {farm_name}",
            content=(
                f"Trang trại {farm_code} ({farm_name}) có chi phí vận hành "
                f"{_number(row['operating_total_cost_vnd'])} VND so với ngân "
                f"sách {_number(row['budget_operating_cost_vnd'])} VND, chênh "
                f"lệch {_number(row['budget_variance_vnd'])} VND, chi phí mỗi "
                f"hecta {_number(row['operating_cost_per_ha_vnd'])} VND, lợi "
                f"nhuận vận hành {_number(row['operating_profit_vnd'])} VND và "
                f"biên lợi nhuận {_number(row['operating_profit_margin_pct'])}%."
            ),
            source_type="cost",
            as_of=as_of,
            tenant_id=scope.tenant_id,
            farm_codes=frozenset({farm_code}),
            warehouse_codes=frozenset(),
        )


def _crop_health(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    as_of,
) -> Iterable[EvidenceChunk]:
    for row in _records(
        snapshot.csv["field_health_status"], "farm_code", "field_code"
    ):
        farm_code = _text(row["farm_code"])
        if not _farm_allowed(scope, farm_code):
            continue
        field_code = _text(row["field_code"])
        yield EvidenceChunk(
            evidence_id=(
                f"crop-health:{_identifier(farm_code)}:{_identifier(field_code)}"
            ),
            title=f"Sức khỏe {field_code} · {farm_code}",
            content=(
                f"Thửa {field_code} tại {farm_code}, cây "
                f"{_text(row['crop_name'])}, có điểm rủi ro "
                f"{_number(row['risk_score'])}, trạng thái "
                f"{_text(row['risk_status'])}, ẩm đất "
                f"{_number(row['soil_moisture_pct'])}%, pH "
                f"{_number(row['soil_ph'])}, số ca sâu bệnh 90 ngày "
                f"{_number(row['pest_cases_90d'])}. Khuyến nghị: "
                f"{_text(row['recommended_action'])}."
            ),
            source_type="crop-health",
            as_of=as_of,
            tenant_id=scope.tenant_id,
            farm_codes=frozenset({farm_code}),
            warehouse_codes=frozenset(),
        )


def _inventory(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    as_of,
) -> Iterable[EvidenceChunk]:
    for row in _records(
        snapshot.csv["inventory_status"], "warehouse_code", "material_code"
    ):
        warehouse_code = _text(row["warehouse_code"])
        if not _warehouse_allowed(scope, warehouse_code):
            continue
        material_code = _text(row["material_code"])
        yield EvidenceChunk(
            evidence_id=(
                f"inventory:{_identifier(warehouse_code)}:"
                f"{_identifier(material_code)}"
            ),
            title=f"Tồn kho {material_code} · {warehouse_code}",
            content=(
                f"Kho {warehouse_code} ({_text(row['warehouse_name'])}) có vật "
                f"tư {material_code} ({_text(row['material_name'])}), tồn "
                f"{_number(row['stock_quantity'])} "
                f"{_text(row['base_unit'])}, giá trị "
                f"{_number(row['inventory_value_vnd'])} VND, số ngày cung ứng "
                f"{_number(row['days_of_supply'])}, trạng thái "
                f"{_text(row['stock_status'])}, lượng đề nghị đặt "
                f"{_number(row['recommended_order_quantity'])}."
            ),
            source_type="inventory",
            as_of=as_of,
            tenant_id=scope.tenant_id,
            farm_codes=frozenset(),
            warehouse_codes=frozenset({warehouse_code}),
        )


def _overview(
    snapshot: ArtifactSnapshot,
    scope: AuthorizedScope,
    as_of,
) -> Iterable[EvidenceChunk]:
    if not scope.farm_tenant_wide:
        return
    rows = _records(snapshot.csv["executive_summary"])
    if not rows:
        return
    row = rows[0]
    yield EvidenceChunk(
        evidence_id="overview:executive-summary",
        title="Tổng quan toàn doanh nghiệp",
        content=(
            f"Toàn doanh nghiệp có doanh thu "
            f"{_number(row['total_revenue_vnd'])} VND, tổng chi phí "
            f"{_number(row['total_cost_vnd'])} VND, lợi nhuận "
            f"{_number(row['profit_vnd'])} VND, biên lợi nhuận "
            f"{_number(row['profit_margin_pct'])}%, sản lượng "
            f"{_number(row['harvest_quantity_kg'])} kg, diện tích canh tác "
            f"{_number(row['cultivated_area_ha'])} ha và "
            f"{_number(row['risk_alerts'])} cảnh báo rủi ro."
        ),
        source_type="overview",
        as_of=as_of,
        tenant_id=scope.tenant_id,
        farm_codes=frozenset(),
        warehouse_codes=frozenset(),
        tenant_wide_only=True,
    )


def _records(frame: pd.DataFrame, *sort_columns: str) -> list[dict[str, Any]]:
    if frame.empty:
        return []
    ordered = (
        frame.sort_values(list(sort_columns), kind="stable")
        if sort_columns
        else frame
    )
    return ordered.to_dict(orient="records")


def _farm_allowed(scope: AuthorizedScope, farm_code: str) -> bool:
    return scope.farm_tenant_wide or farm_code in scope.farm_codes


def _warehouse_allowed(scope: AuthorizedScope, warehouse_code: str) -> bool:
    return (
        scope.warehouse_tenant_wide
        or warehouse_code in scope.warehouse_codes
    )


def _identifier(value: str) -> str:
    normalized = re.sub(r"[^a-z0-9]+", "-", value.casefold()).strip("-")
    if not normalized:
        raise ValueError("evidence resource code is invalid")
    return normalized[:64]


def _text(value: Any) -> str:
    if value is None or bool(pd.isna(value)):
        return "không có"
    normalized = _CONTROL_CHARACTER.sub(" ", str(value))
    normalized = normalized.replace("<", "‹").replace(">", "›")
    return " ".join(normalized.split())[:500]


def _number(value: Any) -> str:
    if value is None or bool(pd.isna(value)):
        return "không có"
    number = float(value)
    if number.is_integer():
        return f"{number:,.0f}"
    return f"{number:,.2f}".rstrip("0").rstrip(".")
