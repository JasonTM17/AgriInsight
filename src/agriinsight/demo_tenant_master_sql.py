from __future__ import annotations

from agriinsight.analytics_snapshot import ArtifactSnapshot
from agriinsight.demo_tenant_contract import DemoContract
from agriinsight.demo_tenant_sql_primitives import master_upsert, select_id


def master_catalog_sql(
    contract: DemoContract,
    snapshot: ArtifactSnapshot,
) -> list[str]:
    lines = [
        master_upsert(
            "farms",
            contract,
            row.farm_code,
            {"display_name": row.farm_name, "active": True},
        )
        for row in snapshot.csv["farms"].itertuples(index=False)
    ]
    lines.extend(
        master_upsert(
            "crops",
            contract,
            row.crop_code,
            {
                "display_name": row.crop_name,
                "scientific_name": None,
                "active": True,
            },
        )
        for row in snapshot.csv["crops"].itertuples(index=False)
    )
    lines.extend(_field_sql(contract, snapshot))
    lines.extend(_season_sql(contract, snapshot))
    lines.extend(
        master_upsert(
            "warehouses",
            contract,
            row.warehouse_code,
            {
                "display_name": row.warehouse_name,
                "location_text": f"Demo master for {row.farm_code}",
                "active": True,
            },
        )
        for row in snapshot.csv["warehouses"].itertuples(index=False)
    )
    lines.extend(
        master_upsert(
            "materials",
            contract,
            row.material_code,
            {
                "display_name": row.material_name,
                "base_unit": str(row.base_unit).upper(),
                "minimum_stock_quantity": row.reorder_point,
                "active": True,
            },
        )
        for row in snapshot.csv["materials"].itertuples(index=False)
    )
    return lines


def _field_sql(
    contract: DemoContract,
    snapshot: ArtifactSnapshot,
) -> list[str]:
    lines = []
    for row in snapshot.csv["fields"].itertuples(index=False):
        lines.append(
            master_upsert(
                "fields",
                contract,
                row.field_code,
                {
                    "farm_id": select_id("farms", contract, row.farm_code),
                    "display_name": row.field_name,
                    "area_hectares": row.area_ha,
                    "latitude": row.latitude,
                    "longitude": row.longitude,
                    "soil_type": row.soil_type,
                    "irrigation_type": row.irrigation_type,
                    "active": True,
                },
            )
        )
    return lines


def _season_sql(
    contract: DemoContract,
    snapshot: ArtifactSnapshot,
) -> list[str]:
    fields = snapshot.csv["fields"].set_index("field_code")
    lines = []
    for row in snapshot.csv["seasons"].itertuples(index=False):
        farm_code = fields.loc[row.field_code, "farm_code"]
        status = str(row.status).upper()
        started_on = row.start_date if status in {"ACTIVE", "COMPLETED"} else None
        ended_on = row.expected_harvest_date if status == "COMPLETED" else None
        lines.append(
            master_upsert(
                "seasons",
                contract,
                row.season_code,
                {
                    "farm_id": select_id("farms", contract, farm_code),
                    "field_id": select_id("fields", contract, row.field_code),
                    "crop_id": select_id("crops", contract, row.crop_code),
                    "display_name": f"{row.season_code} - {row.crop_code}",
                    "planned_start_date": row.start_date,
                    "planned_end_date": row.expected_harvest_date,
                    "started_on": started_on,
                    "ended_on": ended_on,
                    "planted_area_hectares": fields.loc[
                        row.field_code, "area_ha"
                    ],
                    "budget_vnd": row.budget_cost_vnd,
                    "status": status,
                },
            )
        )
    return lines
