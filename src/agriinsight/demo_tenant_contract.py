from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from uuid import UUID

from agriinsight.analytics_snapshot import ArtifactSnapshot, load_artifact_snapshot

DEMO_ROLES = frozenset(
    {
        "DATA_ANALYST",
        "EXECUTIVE",
        "FARM_MANAGER",
        "FIELD_WORKER",
        "INVENTORY_MANAGER",
        "SUPPLIER",
        "TENANT_ADMIN",
    }
)
MASTER_DATASETS = {
    "activities": "silver/activities.csv",
    "crops": "silver/crops.csv",
    "farms": "silver/farms.csv",
    "fields": "silver/fields.csv",
    "materials": "silver/materials.csv",
    "seasons": "silver/seasons.csv",
    "warehouses": "silver/warehouses.csv",
}


class DemoContractError(ValueError):
    """Raised when the non-secret demo contract is incomplete or unsafe."""


@dataclass(frozen=True, slots=True)
class DemoPersona:
    display_name: str
    email: str
    farm_codes: tuple[str, ...]
    profile_id: UUID
    role: str
    subject: str
    warehouse_codes: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class DemoContract:
    issuer: str
    personas: tuple[DemoPersona, ...]
    tenant_code: str
    tenant_display_name: str
    tenant_id: UUID


def load_demo_contract(path: Path) -> DemoContract:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise DemoContractError("Demo tenant contract is unreadable") from error
    if not isinstance(payload, dict):
        raise DemoContractError("Demo tenant contract root must be an object")
    tenant = _object(payload, "tenant")
    raw_personas = payload.get("personas")
    if not isinstance(raw_personas, list):
        raise DemoContractError("Demo personas must be an array")
    personas = tuple(_persona(value) for value in raw_personas)
    roles = {persona.role for persona in personas}
    if len(personas) != 7 or roles != DEMO_ROLES:
        raise DemoContractError("Demo contract must contain each fixed persona once")
    if len({persona.profile_id for persona in personas}) != 7:
        raise DemoContractError("Demo profile IDs must be unique")
    if len({persona.subject for persona in personas}) != 7:
        raise DemoContractError("Demo subjects must be unique")
    issuer = _text(payload, "issuer")
    if not issuer.startswith(("http://localhost:", "http://127.0.0.1:")):
        raise DemoContractError("Demo issuer must be an explicit loopback origin")
    return DemoContract(
        issuer=issuer,
        personas=personas,
        tenant_code=_text(tenant, "code"),
        tenant_display_name=_text(tenant, "displayName"),
        tenant_id=UUID(_text(tenant, "id")),
    )


def load_demo_snapshot(artifact_root: Path) -> ArtifactSnapshot:
    return load_artifact_snapshot(
        artifact_root,
        csv_datasets=MASTER_DATASETS,
    )


def validate_persona_scopes(
    contract: DemoContract,
    snapshot: ArtifactSnapshot,
) -> None:
    farm_codes = set(snapshot.csv["farms"]["farm_code"])
    warehouse_codes = set(snapshot.csv["warehouses"]["warehouse_code"])
    for persona in contract.personas:
        if not set(persona.farm_codes).issubset(farm_codes):
            raise DemoContractError("Persona farm scope is outside verified masters")
        if not set(persona.warehouse_codes).issubset(warehouse_codes):
            raise DemoContractError("Persona warehouse scope is outside verified masters")


def _persona(value: Any) -> DemoPersona:
    if not isinstance(value, dict):
        raise DemoContractError("Each demo persona must be an object")
    farm_codes = _text_list(value, "farmCodes")
    warehouse_codes = _text_list(value, "warehouseCodes")
    return DemoPersona(
        display_name=_text(value, "displayName"),
        email=_text(value, "email"),
        farm_codes=farm_codes,
        profile_id=UUID(_text(value, "profileId")),
        role=_text(value, "role"),
        subject=_text(value, "subject"),
        warehouse_codes=warehouse_codes,
    )


def _object(value: dict[str, Any], key: str) -> dict[str, Any]:
    selected = value.get(key)
    if not isinstance(selected, dict):
        raise DemoContractError(f"{key} must be an object")
    return selected


def _text(value: dict[str, Any], key: str) -> str:
    selected = value.get(key)
    if not isinstance(selected, str) or not selected.strip():
        raise DemoContractError(f"{key} must be non-empty text")
    return selected.strip()


def _text_list(value: dict[str, Any], key: str) -> tuple[str, ...]:
    selected = value.get(key, [])
    if not isinstance(selected, list) or any(
        not isinstance(item, str) or not item for item in selected
    ):
        raise DemoContractError(f"{key} must be a text array")
    return tuple(selected)
