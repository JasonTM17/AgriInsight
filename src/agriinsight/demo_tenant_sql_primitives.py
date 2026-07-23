from __future__ import annotations

from dataclasses import dataclass
from typing import Any
from uuid import UUID, uuid5

import pandas as pd

from agriinsight.demo_tenant_contract import DemoContract

_NAMESPACE = UUID("20000000-0000-4000-8000-00000000a611")


@dataclass(frozen=True, slots=True)
class SqlExpression:
    value: str


def deterministic_id(kind: str, natural_key: str) -> UUID:
    return uuid5(_NAMESPACE, f"{kind}:{natural_key}")


def literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def select_id(table: str, contract: DemoContract, code: str) -> SqlExpression:
    tenant = literal(str(contract.tenant_id))
    return SqlExpression(
        f"(SELECT id FROM {table} WHERE tenant_id = {tenant}::uuid "
        f"AND code = {literal(code)})"
    )


def master_upsert(
    table: str,
    contract: DemoContract,
    code: str,
    values: dict[str, Any],
) -> str:
    tenant = str(contract.tenant_id)
    columns = ["id", "tenant_id", "code", *values]
    sql_values = [
        f"{literal(str(deterministic_id(table, code)))}::uuid",
        f"{literal(tenant)}::uuid",
        literal(code),
        *(_sql_value(value) for value in values.values()),
    ]
    updates = ", ".join(
        f"{column} = EXCLUDED.{column}" for column in values
    )
    return (
        f"INSERT INTO {table} ({', '.join(columns)}) VALUES "
        f"({', '.join(sql_values)}) "
        f"ON CONFLICT (tenant_id, code) DO UPDATE SET {updates}, "
        "updated_at = CURRENT_TIMESTAMP;"
    )


def _sql_value(value: Any) -> str:
    if isinstance(value, SqlExpression):
        return value.value
    if value is None or (not isinstance(value, str) and pd.isna(value)):
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return str(value)
    return literal(str(value))
