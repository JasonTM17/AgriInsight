from __future__ import annotations

from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class UpstreamModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class CurrentUser(UpstreamModel):
    assurance: str | None
    displayName: str | None
    email: str | None
    permissions: list[str]
    profileId: UUID
    roles: list[str]
    tenantCode: str
    tenantId: UUID


class FarmItem(UpstreamModel):
    active: bool
    code: str
    displayName: str
    id: UUID
    version: int


class WarehouseItem(UpstreamModel):
    active: bool
    code: str
    displayName: str
    id: UUID
    locationText: str | None = None
    version: int


class FarmPage(UpstreamModel):
    hasMore: bool
    items: list[FarmItem]
    limit: int = Field(ge=1, le=100)
    offset: int = Field(ge=0, le=10_000)


class WarehousePage(UpstreamModel):
    hasMore: bool
    items: list[WarehouseItem]
    limit: int = Field(ge=1, le=100)
    offset: int = Field(ge=0, le=10_000)
