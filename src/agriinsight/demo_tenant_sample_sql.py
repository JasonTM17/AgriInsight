from __future__ import annotations

from datetime import timedelta

import pandas as pd

from agriinsight.analytics_snapshot import ArtifactSnapshot
from agriinsight.demo_tenant_contract import DemoContract
from agriinsight.demo_tenant_sql_primitives import (
    deterministic_id,
    literal,
    master_upsert,
    select_id,
)

FIELD_WORKER_EMPLOYEE_CODE = "DEMO-FIELD-WORKER"
WORK_ASSIGNMENT_LIMIT = 3

ACTIVITY_TYPES = {
    "Bón phân": "FERTILIZATION",
    "Gieo trồng": "PLANTING",
    "Kiểm tra sâu bệnh": "PEST_INSPECTION",
    "Làm cỏ": "WEEDING",
    "Phun thuốc": "PEST_CONTROL",
    "Tưới nước": "IRRIGATION",
}


def field_worker_employee_sql(contract: DemoContract) -> list[str]:
    field_worker = next(
        persona for persona in contract.personas if persona.role == "FIELD_WORKER"
    )
    return [
        master_upsert(
            "employees",
            contract,
            FIELD_WORKER_EMPLOYEE_CODE,
            {
                "display_name": field_worker.display_name,
                "job_title": "Field Worker",
                "active": True,
            },
        )
    ]


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
    lines.extend(_activity_assignment_sql(contract, supported))
    return lines, len(supported)


def _activity_assignment_sql(
    contract: DemoContract,
    supported: pd.DataFrame,
) -> list[str]:
    tenant = literal(str(contract.tenant_id))
    employee_id = deterministic_id("employees", FIELD_WORKER_EMPLOYEE_CODE)
    statements: list[str] = []
    for row in supported.head(WORK_ASSIGNMENT_LIMIT).itertuples(index=False):
        activity_id = deterministic_id("activities", str(row.activity_id))
        assignment_id = deterministic_id(
            "activity-assignment",
            f"{employee_id}:{row.activity_id}",
        )
        statements.append(
            "UPDATE activity_assignees AS existing "
            "SET revoked_at = NULL "
            f"WHERE existing.id = {literal(str(assignment_id))}::uuid "
            f"AND existing.tenant_id = {tenant}::uuid "
            f"AND existing.activity_id = {literal(str(activity_id))}::uuid "
            f"AND existing.employee_id = {literal(str(employee_id))}::uuid;"
        )
        statements.append(
            "INSERT INTO activity_assignees "
            "(id, tenant_id, activity_id, employee_id, revoked_at) "
            f"SELECT {literal(str(assignment_id))}::uuid, {tenant}::uuid, "
            "activity.id, employee.id, NULL "
            "FROM activities AS activity "
            "JOIN employees AS employee "
            "ON employee.tenant_id = activity.tenant_id "
            f"AND employee.id = {literal(str(employee_id))}::uuid "
            "AND employee.active "
            f"WHERE activity.tenant_id = {tenant}::uuid "
            f"AND activity.id = {literal(str(activity_id))}::uuid "
            "AND NOT EXISTS (SELECT 1 FROM activity_assignees AS existing "
            f"WHERE existing.tenant_id = {tenant}::uuid "
            "AND existing.activity_id = activity.id "
            "AND existing.employee_id = employee.id "
            "AND existing.revoked_at IS NULL);"
        )
    return statements
