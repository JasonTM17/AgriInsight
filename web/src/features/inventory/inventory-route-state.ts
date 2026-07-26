import { z } from "zod";

import {
  INVENTORY_MAX_OFFSET,
  INVENTORY_PAGE_SIZE,
  INVENTORY_TRANSACTION_KINDS,
  type InventoryTransactionKind
} from "./inventory-generated-client-adapter";

type RawValue = string | readonly string[] | undefined;

export type InventoryRouteFilters = Readonly<{
  materialId?: string;
  lowStock?: true;
  kind?: InventoryTransactionKind;
  from?: string;
  to?: string;
}>;

export type InventoryRouteState = Readonly<{
  warehouseId?: string;
  filters: InventoryRouteFilters;
  balanceOffset: number;
  lotOffset: number;
  txOffset: number;
}>;

export type InventoryHrefOverrides = Readonly<{
  warehouseId?: string;
  materialId?: string;
  lowStock?: boolean;
  kind?: InventoryTransactionKind;
  from?: string;
  to?: string;
  balanceOffset?: number;
  lotOffset?: number;
  txOffset?: number;
}>;

const uuidSchema = z.uuid();
const kindSchema = z.enum(INVENTORY_TRANSACTION_KINDS);
const isoDateSchema = z.iso.date();
const offsetSchema = z.string()
  .regex(/^(?:0|[1-9]\d*)$/)
  .transform(Number)
  .pipe(
    z.number()
      .int()
      .min(0)
      .max(INVENTORY_MAX_OFFSET)
      .refine((value) => value % INVENTORY_PAGE_SIZE === 0)
  );

export function parseInventoryRouteState(
  input: Readonly<Record<string, RawValue>>
): InventoryRouteState | null {
  if (Object.values(input).some(Array.isArray)) return null;
  const warehouseId = scalar(input.warehouseId);
  const materialId = scalar(input.materialId);
  const rawLowStock = scalar(input.lowStock);
  const rawKind = scalar(input.kind);
  const from = scalar(input.from);
  const to = scalar(input.to);
  const balanceOffset = parseOffset(scalar(input.balanceOffset));
  const lotOffset = parseOffset(scalar(input.lotOffset));
  const txOffset = parseOffset(scalar(input.txOffset));
  if (balanceOffset === null || lotOffset === null || txOffset === null) {
    return null;
  }
  if (warehouseId && !uuidSchema.safeParse(warehouseId).success) return null;
  if (materialId && !uuidSchema.safeParse(materialId).success) return null;
  if (rawLowStock !== undefined && rawLowStock !== "true") return null;
  if (rawKind !== undefined && !kindSchema.safeParse(rawKind).success) {
    return null;
  }
  if (from !== undefined && !isoDateSchema.safeParse(from).success) return null;
  if (to !== undefined && !isoDateSchema.safeParse(to).success) return null;
  if (from !== undefined && to !== undefined && from > to) return null;
  const lowStock = rawLowStock === "true" ? true : undefined;
  const kind = rawKind as InventoryTransactionKind | undefined;
  const hasDependentFilters = Boolean(
    materialId || lowStock || kind || from || to
  );
  const hasDependentOffsets = balanceOffset > 0 || lotOffset > 0 || txOffset > 0;
  if ((hasDependentFilters || hasDependentOffsets) && !warehouseId) return null;
  return {
    warehouseId,
    balanceOffset,
    lotOffset,
    txOffset,
    filters: { materialId, lowStock, kind, from, to }
  };
}

export function inventoryHref(
  state: InventoryRouteState,
  overrides: InventoryHrefOverrides = {}
): string {
  const warehouseId = overrides.warehouseId ?? state.warehouseId;
  if (!warehouseId) return "/inventory";
  const query = new URLSearchParams();
  query.set("warehouseId", warehouseId);
  const materialId = overrides.materialId ?? state.filters.materialId;
  const lowStock = overrides.lowStock ?? state.filters.lowStock;
  const kind = overrides.kind ?? state.filters.kind;
  const from = overrides.from ?? state.filters.from;
  const to = overrides.to ?? state.filters.to;
  const balanceOffset = overrides.balanceOffset ?? state.balanceOffset;
  const lotOffset = overrides.lotOffset ?? state.lotOffset;
  const txOffset = overrides.txOffset ?? state.txOffset;
  if (materialId) query.set("materialId", materialId);
  if (lowStock) query.set("lowStock", "true");
  if (kind) query.set("kind", kind);
  if (from) query.set("from", from);
  if (to) query.set("to", to);
  if (balanceOffset > 0) query.set("balanceOffset", String(balanceOffset));
  if (lotOffset > 0) query.set("lotOffset", String(lotOffset));
  if (txOffset > 0) query.set("txOffset", String(txOffset));
  return `/inventory?${query.toString()}`;
}

function scalar(value: RawValue): string | undefined {
  return typeof value === "string" ? value : undefined;
}

function parseOffset(value: string | undefined): number | null {
  if (value === undefined) return 0;
  const parsed = offsetSchema.safeParse(value);
  return parsed.success ? parsed.data : null;
}
