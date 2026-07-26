import "server-only";

import { z } from "zod";

import type { components } from "@/server/generated/backend/schema";

type GeneratedWarehouse = components["schemas"]["WarehouseResponse"];
type GeneratedWarehousePage = components["schemas"]["WarehousePageResponse"];
type GeneratedMaterial = components["schemas"]["MaterialResponse"];
type GeneratedMaterialPage = components["schemas"]["MaterialPageResponse"];
type GeneratedSupplier = components["schemas"]["SupplierResponse"];
type GeneratedSupplierPage = components["schemas"]["SupplierPageResponse"];
type GeneratedStockBalance = components["schemas"]["StockBalanceResponse"];
type GeneratedStockBalancePage =
  components["schemas"]["StockBalancePageResponse"];
type GeneratedStockLot = components["schemas"]["StockLotResponse"];
type GeneratedStockLotPage = components["schemas"]["StockLotPageResponse"];
type GeneratedInventoryTransaction =
  components["schemas"]["InventoryTransactionResponse"];
type GeneratedInventoryTransactionPage =
  components["schemas"]["InventoryTransactionPageResponse"];

export const INVENTORY_PAGE_SIZE = 50;
export const INVENTORY_MAX_OFFSET = 10_000;
export const INVENTORY_TRANSACTION_KINDS = [
  "RECEIPT",
  "ISSUE",
  "REVERSAL"
] as const;

const inventoryUnits = ["KG", "LITER", "PIECE"] as const;
const instantSchema = z.iso.datetime({ offset: true });
const dateSchema = z.iso.date();
const uuidSchema = z.uuid();
const optionalRuntimeValue = <Schema extends z.ZodType>(schema: Schema) =>
  schema.nullish().transform((value) => value ?? undefined);

const warehouseShape = {
  active: z.boolean(),
  code: z.string().min(1),
  displayName: z.string().min(1),
  id: uuidSchema,
  locationText: optionalRuntimeValue(z.string()),
  version: z.number().int().nonnegative()
} satisfies Record<keyof GeneratedWarehouse, z.ZodType>;

const materialShape = {
  active: z.boolean(),
  baseUnit: z.enum(inventoryUnits),
  code: z.string().min(1),
  displayName: z.string().min(1),
  id: uuidSchema,
  minimumStockQuantity: optionalRuntimeValue(z.number().finite().nonnegative()),
  version: z.number().int().nonnegative()
} satisfies Record<keyof GeneratedMaterial, z.ZodType>;

const supplierShape = {
  active: z.boolean(),
  code: z.string().min(1),
  displayName: z.string().min(1),
  id: uuidSchema,
  version: z.number().int().nonnegative()
} satisfies Record<keyof GeneratedSupplier, z.ZodType>;

const stockBalanceShape = {
  id: uuidSchema,
  inventoryValueVnd: z.number().finite(),
  lowStock: z.boolean(),
  materialCode: z.string().min(1),
  materialId: uuidSchema,
  materialName: z.string().min(1),
  minimumStockQuantity: optionalRuntimeValue(z.number().finite().nonnegative()),
  quantityOnHand: z.number().finite(),
  unit: z.enum(inventoryUnits),
  version: z.number().int().nonnegative(),
  warehouseCode: z.string().min(1),
  warehouseId: uuidSchema
} satisfies Record<keyof GeneratedStockBalance, z.ZodType>;

const stockLotShape = {
  availableQuantity: z.number().finite(),
  batchCode: z.string().min(1),
  expired: z.boolean(),
  expiringSoon: z.boolean(),
  expiryDate: dateSchema,
  id: uuidSchema,
  materialCode: z.string().min(1),
  materialId: uuidSchema,
  materialName: z.string().min(1),
  originalReceiptId: uuidSchema,
  receivedAt: instantSchema,
  receivedQuantity: z.number().finite(),
  supplierCode: z.string().min(1),
  supplierId: uuidSchema,
  unit: z.enum(inventoryUnits),
  unitCostVnd: z.number().finite(),
  version: z.number().int().nonnegative(),
  warehouseCode: z.string().min(1),
  warehouseId: uuidSchema
} satisfies Record<keyof GeneratedStockLot, z.ZodType>;

const inventoryTransactionShape = {
  batchCode: optionalRuntimeValue(z.string().min(1)),
  expiryDate: optionalRuntimeValue(dateSchema),
  id: uuidSchema,
  kind: z.enum(INVENTORY_TRANSACTION_KINDS),
  materialId: uuidSchema,
  occurredAt: instantSchema,
  procurementEffectVnd: z.number().finite(),
  quantityBase: z.number().finite(),
  reason: optionalRuntimeValue(z.string().min(1)),
  recordedByProfileId: uuidSchema,
  referenceCode: optionalRuntimeValue(z.string().min(1)),
  reversalOf: optionalRuntimeValue(uuidSchema),
  signedQuantityEffect: z.number().finite(),
  supplierId: optionalRuntimeValue(uuidSchema),
  unit: z.enum(inventoryUnits),
  unitCostVnd: optionalRuntimeValue(z.number().finite()),
  version: z.number().int().nonnegative(),
  warehouseId: uuidSchema
} satisfies Record<keyof GeneratedInventoryTransaction, z.ZodType>;

export const warehouseSchema = z.object(warehouseShape).strict().readonly();
export const materialSchema = z.object(materialShape).strict().readonly();
export const supplierSchema = z.object(supplierShape).strict().readonly();
export const stockBalanceSchema =
  z.object(stockBalanceShape).strict().readonly();
export const stockLotSchema = z.object(stockLotShape).strict().readonly();
export const inventoryTransactionSchema =
  z.object(inventoryTransactionShape).strict().readonly();

const warehousePageShape = {
  hasMore: z.boolean(),
  items: z.array(warehouseSchema).max(100).readonly(),
  limit: z.literal(100),
  offset: z.number().int().min(0).max(INVENTORY_MAX_OFFSET)
} satisfies Record<keyof GeneratedWarehousePage, z.ZodType>;

const materialPageShape = {
  hasMore: z.boolean(),
  items: z.array(materialSchema).max(100).readonly(),
  limit: z.literal(100),
  offset: z.number().int().min(0).max(INVENTORY_MAX_OFFSET)
} satisfies Record<keyof GeneratedMaterialPage, z.ZodType>;

const supplierPageShape = {
  hasMore: z.boolean(),
  items: z.array(supplierSchema).max(100).readonly(),
  limit: z.literal(100),
  offset: z.number().int().min(0).max(INVENTORY_MAX_OFFSET)
} satisfies Record<keyof GeneratedSupplierPage, z.ZodType>;

const stockBalancePageShape = {
  hasMore: z.boolean(),
  items: z.array(stockBalanceSchema).max(INVENTORY_PAGE_SIZE).readonly(),
  limit: z.literal(INVENTORY_PAGE_SIZE),
  offset: z.number().int().min(0).max(INVENTORY_MAX_OFFSET)
} satisfies Record<keyof GeneratedStockBalancePage, z.ZodType>;

const stockLotPageShape = {
  hasMore: z.boolean(),
  items: z.array(stockLotSchema).max(INVENTORY_PAGE_SIZE).readonly(),
  limit: z.literal(INVENTORY_PAGE_SIZE),
  offset: z.number().int().min(0).max(INVENTORY_MAX_OFFSET)
} satisfies Record<keyof GeneratedStockLotPage, z.ZodType>;

const inventoryTransactionPageShape = {
  hasMore: z.boolean(),
  items: z.array(inventoryTransactionSchema).max(INVENTORY_PAGE_SIZE).readonly(),
  limit: z.literal(INVENTORY_PAGE_SIZE),
  offset: z.number().int().min(0).max(INVENTORY_MAX_OFFSET)
} satisfies Record<keyof GeneratedInventoryTransactionPage, z.ZodType>;

export const warehousePageSchema =
  z.object(warehousePageShape).strict().readonly();
export const materialPageSchema =
  z.object(materialPageShape).strict().readonly();
export const supplierPageSchema =
  z.object(supplierPageShape).strict().readonly();
export const stockBalancePageSchema =
  z.object(stockBalancePageShape).strict().readonly();
export const stockLotPageSchema =
  z.object(stockLotPageShape).strict().readonly();
export const inventoryTransactionPageSchema =
  z.object(inventoryTransactionPageShape).strict().readonly();

export type Warehouse = z.output<typeof warehouseSchema>;
export type Material = z.output<typeof materialSchema>;
export type Supplier = z.output<typeof supplierSchema>;
export type StockBalance = z.output<typeof stockBalanceSchema>;
export type StockLot = z.output<typeof stockLotSchema>;
export type InventoryTransaction = z.output<typeof inventoryTransactionSchema>;
export type InventoryTransactionKind = InventoryTransaction["kind"];
