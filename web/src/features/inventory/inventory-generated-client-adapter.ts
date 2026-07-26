import "server-only";

import type { ZodType } from "zod";

import type { AllowedOperationName } from "@/server/bff/allowed-operation";
import { executeAllowedOperation } from "@/server/bff/upstream-client";
import type { WebEnvironment } from "@/server/config/environment";

import {
  INVENTORY_MAX_OFFSET,
  INVENTORY_PAGE_SIZE,
  inventoryTransactionSchema,
  inventoryTransactionPageSchema,
  materialPageSchema,
  stockBalancePageSchema,
  stockLotPageSchema,
  supplierPageSchema,
  warehousePageSchema,
  type InventoryTransaction,
  type InventoryTransactionKind,
  type Material,
  type StockBalance,
  type StockLot,
  type Supplier,
  type Warehouse
} from "./inventory-generated-contract-schemas";

export {
  INVENTORY_MAX_OFFSET,
  INVENTORY_PAGE_SIZE,
  INVENTORY_TRANSACTION_KINDS,
  type InventoryTransaction,
  type InventoryTransactionKind,
  type Material,
  type StockBalance,
  type StockLot,
  type Supplier,
  type Warehouse
} from "./inventory-generated-contract-schemas";

const CATALOG_PAGE_SIZE = 100;

export type InventoryPage<Item> = Readonly<{
  items: readonly Item[];
  limit: number;
  offset: number;
  hasMore: boolean;
}>;

export type InventoryReadErrorKind =
  | "unauthenticated"
  | "denied"
  | "not_found"
  | "failure";

const ERROR_MESSAGES: Readonly<Record<InventoryReadErrorKind, string>> = {
  unauthenticated: "Phiên làm việc chưa được xác thực.",
  denied: "Dữ liệu kho không khả dụng trong phạm vi này.",
  not_found: "Không tìm thấy bản ghi kho trong phạm vi này.",
  failure: "Không thể tải dữ liệu kho."
};

export class InventoryReadError extends Error {
  constructor(
    readonly kind: InventoryReadErrorKind,
    readonly status: 401 | 403 | 404 | 502
  ) {
    super(ERROR_MESSAGES[kind]);
    this.name = "InventoryReadError";
  }
}

export type InventoryReadContext = Readonly<{
  env: WebEnvironment;
  accessToken: string;
  correlationId: string;
}>;

export async function getVisibleWarehouses(
  context: InventoryReadContext
): Promise<readonly Warehouse[]> {
  return collectCatalog((offset) =>
    requestInventoryPayload(
      context,
      "warehouseCatalog",
      warehousePageSchema,
      { active: true, limit: CATALOG_PAGE_SIZE, offset }
    )
  );
}

export async function getSupplierCatalog(
  context: InventoryReadContext
): Promise<readonly Supplier[]> {
  return collectCatalog((offset) =>
    requestInventoryPayload(
      context,
      "supplierCatalog",
      supplierPageSchema,
      { active: true, limit: CATALOG_PAGE_SIZE, offset }
    )
  );
}

export async function getMaterialCatalog(
  context: InventoryReadContext
): Promise<readonly Material[]> {
  return collectCatalog((offset) =>
    requestInventoryPayload(
      context,
      "materialCatalog",
      materialPageSchema,
      { active: true, limit: CATALOG_PAGE_SIZE, offset }
    )
  );
}

export async function getStockBalancePage(
  context: InventoryReadContext,
  warehouseId: string,
  offset: number,
  filters: Readonly<{ lowStock?: boolean; materialId?: string }> = {}
): Promise<InventoryPage<StockBalance>> {
  const page = await requestInventoryPayload(
    context,
    "inventoryBalances",
    stockBalancePageSchema,
    {
      limit: INVENTORY_PAGE_SIZE,
      lowStock: filters.lowStock,
      materialId: filters.materialId,
      offset,
      warehouseId
    }
  );
  return assertRequestedPage(page, offset);
}

export async function getStockLotPage(
  context: InventoryReadContext,
  warehouseId: string,
  offset: number,
  filters: Readonly<{ materialId?: string }> = {}
): Promise<InventoryPage<StockLot>> {
  const page = await requestInventoryPayload(
    context,
    "inventoryLots",
    stockLotPageSchema,
    {
      limit: INVENTORY_PAGE_SIZE,
      materialId: filters.materialId,
      offset,
      warehouseId
    }
  );
  return assertRequestedPage(page, offset);
}

export async function getInventoryTransactionPage(
  context: InventoryReadContext,
  warehouseId: string,
  offset: number,
  filters: Readonly<{
    kind?: InventoryTransactionKind;
    materialId?: string;
    occurredFrom?: string;
    occurredTo?: string;
  }> = {}
): Promise<InventoryPage<InventoryTransaction>> {
  const page = await requestInventoryPayload(
    context,
    "inventoryTransactions",
    inventoryTransactionPageSchema,
    {
      kind: filters.kind,
      limit: INVENTORY_PAGE_SIZE,
      materialId: filters.materialId,
      occurredFrom: filters.occurredFrom,
      occurredTo: filters.occurredTo,
      offset,
      warehouseId
    }
  );
  return assertRequestedPage(page, offset);
}

export type InventoryTransactionDetail = Readonly<{
  etag: string;
  transaction: InventoryTransaction;
}>;

export async function getInventoryTransactionDetail(
  context: InventoryReadContext,
  transactionId: string
): Promise<InventoryTransactionDetail> {
  try {
    const response = await executeAllowedOperation(
      context.env,
      "inventoryTransactionById",
      context.accessToken,
      context.correlationId,
      {},
      { id: transactionId }
    );
    if (!response.ok) throw inventoryErrorForStatus(response.status);
    const etag = response.headers.get("ETag");
    if (!etag || !/^"\d{1,19}"$/.test(etag)) {
      throw new InventoryReadError("failure", 502);
    }
    return {
      etag,
      transaction: inventoryTransactionSchema.parse(await response.json())
    };
  } catch (error) {
    if (error instanceof InventoryReadError) throw error;
    throw new InventoryReadError("failure", 502);
  }
}

async function requestInventoryPayload<Output>(
  context: InventoryReadContext,
  operation: AllowedOperationName,
  schema: ZodType<Output>,
  query: Readonly<Record<string, boolean | number | string | undefined>>,
  pathParameters: Readonly<Record<string, string>> = {}
): Promise<Output> {
  try {
    const compactedQuery = compactQuery(query);
    const response = Object.keys(pathParameters).length > 0
      ? await executeAllowedOperation(
          context.env,
          operation,
          context.accessToken,
          context.correlationId,
          compactedQuery,
          pathParameters
        )
      : await executeAllowedOperation(
          context.env,
          operation,
          context.accessToken,
          context.correlationId,
          compactedQuery
        );
    if (!response.ok) throw inventoryErrorForStatus(response.status);
    return schema.parse(await response.json());
  } catch (error) {
    if (error instanceof InventoryReadError) throw error;
    throw new InventoryReadError("failure", 502);
  }
}

async function collectCatalog<Item>(
  loadPage: (offset: number) => Promise<InventoryPage<Item>>
): Promise<readonly Item[]> {
  const items: Item[] = [];
  let offset = 0;
  while (true) {
    const page = await loadPage(offset);
    if (page.limit !== CATALOG_PAGE_SIZE || page.offset !== offset) {
      throw new InventoryReadError("failure", 502);
    }
    items.push(...page.items);
    if (!page.hasMore) return items;
    offset += CATALOG_PAGE_SIZE;
    if (offset > INVENTORY_MAX_OFFSET) {
      throw new InventoryReadError("failure", 502);
    }
  }
}

function compactQuery(
  query: Readonly<Record<string, boolean | number | string | undefined>>
): Readonly<Record<string, boolean | number | string>> {
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => value !== undefined)
  ) as Readonly<Record<string, boolean | number | string>>;
}

function assertRequestedPage<Item>(
  page: InventoryPage<Item>,
  offset: number
): InventoryPage<Item> {
  if (page.limit !== INVENTORY_PAGE_SIZE || page.offset !== offset) {
    throw new InventoryReadError("failure", 502);
  }
  return page;
}

function inventoryErrorForStatus(status: number): InventoryReadError {
  if (status === 401) return new InventoryReadError("unauthenticated", 401);
  if (status === 403) return new InventoryReadError("denied", 403);
  if (status === 404) return new InventoryReadError("not_found", 404);
  return new InventoryReadError("failure", 502);
}
