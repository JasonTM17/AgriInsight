from __future__ import annotations

from datetime import timedelta

import pandas as pd

from agriinsight.analytics_snapshot import ArtifactSnapshot
from agriinsight.demo_tenant_contract import DemoContract
from agriinsight.demo_tenant_sql_primitives import (
    literal,
    master_upsert,
    select_id,
)

ACTIVITY_TYPES = {
    "Bón phân": "FERTILIZATION",
    "Gieo trồng": "PLANTING",
    "Kiểm tra sâu bệnh": "PEST_INSPECTION",
    "Làm cỏ": "WEEDING",
    "Phun thuốc": "PEST_CONTROL",
    "Tưới nước": "IRRIGATION",
}


def activity_sample_sql(
    contract: DemoContract,
    snapshot: ArtifactSnapshot,
    limit: int,
) -> tuple[list[str], int]:
    tenant = literal(str(contract.tenant_id))
    lines = [
        (
            "INSERT INTO activity_types "
            "(tenant_id, code, display_name, active) VALUES "
            f"({tenant}::uuid, {literal(code)}, {literal(label)}, TRUE) "
            "ON CONFLICT (tenant_id, code) DO UPDATE SET "
            "display_name = EXCLUDED.display_name, active = TRUE, "
            "updated_at = CURRENT_TIMESTAMP;"
        )
        for label, code in sorted(ACTIVITY_TYPES.items())
    ]
    supported = snapshot.csv["activities"]
    supported = supported[supported["activity_type"].isin(ACTIVITY_TYPES)].head(limit)
    for row in supported.itertuples(index=False):
        occurred = pd.Timestamp(row.occurred_at)
        lines.append(
            master_upsert(
                "activities",
                contract,
                str(row.activity_id),
                {
                    "farm_id": select_id("farms", contract, row.farm_code),
                    "field_id": select_id("fields", contract, row.field_code),
                    "season_id": select_id("seasons", contract, row.season_code),
                    "activity_type_code": ACTIVITY_TYPES[row.activity_type],
                    "title": f"{row.activity_type} - demo",
                    "description": row.notes,
                    "planned_start_at": occurred.isoformat(),
                    "due_at": (occurred + timedelta(hours=8)).isoformat(),
                    "started_at": occurred.isoformat(),
                    "completed_at": (occurred + timedelta(hours=4)).isoformat(),
                    "cancelled_at": None,
                    "status": "COMPLETED",
                },
            )
        )
    return lines, len(supported)
