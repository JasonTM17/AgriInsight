import "server-only";

import { getAnalyticsPayload } from "@/server/clients/analytics";
import type { WebEnvironment } from "@/server/config/environment";

import {
  getInventoryTransactionPage,
  getMaterialCatalog,
  getStockBalancePage,
  getStockLotPage,
  getSupplierCatalog,
  getVisibleWarehouses,
  type InventoryPage,
  type InventoryReadContext,
  type InventoryTransaction,
  type Material,
  type StockBalance,
  type StockLot,
  type Supplier,
  type Warehouse
} from "./inventory-generated-client-adapter";
import type { InventoryRouteState } from "./inventory-route-state";
import {
  parseScopedInventoryAnalytics,
  type InventoryAnalyticsEnvelope
} from "./inventory-analytics-contract-schema";

export type SourceResult<T> =
  | Readonly<{ status: "ready"; data: T }>
  | Readonly<{ status: "failed"; message: string; correlationId: string }>;

export type InventoryMasters = Readonly<{
  suppliers: readonly Supplier[];
  materials: readonly Material[];
}>;

export type InventoryViewModel =
  | Readonly<{ kind: "picker"; warehouses: readonly Warehouse[] }>
  | Readonly<{ kind: "foreign_warehouse" }>
  | Readonly<{ kind: "foreign_material" }>
  | Readonly<{
      kind: "ready";
      warehouses: readonly Warehouse[];
      selectedWarehouse: Warehouse;
      balances: SourceResult<InventoryPage<StockBalance>>;
      lots: SourceResult<InventoryPage<StockLot>>;
      transactions: SourceResult<InventoryPage<InventoryTransaction>>;
      analytics: SourceResult<InventoryAnalyticsEnvelope>;
      partial: boolean;
      materials: SourceResult<readonly Material[]>;
      masters: SourceResult<InventoryMasters> | null;
      mastersAvailable: boolean;
    }>;

export type LoadInventoryViewModelInput = Readonly<{
  env: WebEnvironment;
  accessToken: string;
  correlationId: string;
  state: InventoryRouteState;
  canManage: boolean;
}>;

export async function loadInventoryViewModel({
  env,
  accessToken,
  correlationId,
  state,
  canManage
}: LoadInventoryViewModelInput): Promise<InventoryViewModel> {
  const context: InventoryReadContext = { env, accessToken, correlationId };
  const warehouses = await getVisibleWarehouses(context);
  if (!state.warehouseId) {
    return { kind: "picker", warehouses };
  }
  const selectedWarehouse = warehouses.find(
    (warehouse) => warehouse.id === state.warehouseId
  );
  if (!selectedWarehouse) return { kind: "foreign_warehouse" };
  const materialsResult = await Promise.allSettled([
    getMaterialCatalog(context)
  ]).then(([result]) => result);
  if (
    state.filters.materialId
    && (
      materialsResult.status === "rejected"
      || !materialsResult.value.some(
        (material) => material.id === state.filters.materialId
      )
    )
  ) {
    return { kind: "foreign_material" };
  }

  const [balancesResult, lotsResult, transactionsResult, analyticsResult] =
    await Promise.allSettled([
      getStockBalancePage(context, selectedWarehouse.id, state.balanceOffset, {
        lowStock: state.filters.lowStock,
        materialId: state.filters.materialId
      }),
      getStockLotPage(context, selectedWarehouse.id, state.lotOffset, {
        materialId: state.filters.materialId
      }),
      getInventoryTransactionPage(context, selectedWarehouse.id, state.txOffset, {
        kind: state.filters.kind,
        materialId: state.filters.materialId,
        occurredFrom: toOccurredFromInstant(state.filters.from),
        occurredTo: toOccurredToInstant(state.filters.to)
      }),
      getAnalyticsPayload(
        env,
        "analyticsInventory",
        accessToken,
        correlationId,
        {
          limit: 50,
          offset: 0,
          warehouse_code: selectedWarehouse.code
        }
      ).then((value) =>
        parseScopedInventoryAnalytics(value, selectedWarehouse.code)
      )
    ]);

  const analytics: SourceResult<InventoryAnalyticsEnvelope> =
    analyticsResult.status === "fulfilled"
      ? { status: "ready", data: analyticsResult.value }
      : sourceFailure(correlationId, "Không thể tải phân tích Gold cho kho này.");

  const materials = settledSource(
    materialsResult,
    correlationId,
    "Không thể tải danh mục vật tư hiện hành."
  );
  const { masters, mastersAvailable } = canManage
    ? await loadInventoryMasters(context, correlationId, materials)
    : { masters: null, mastersAvailable: false };

  return {
    kind: "ready",
    warehouses,
    selectedWarehouse,
    balances: scopedOperationalSource(
      balancesResult,
      selectedWarehouse.id,
      correlationId,
      "Không thể tải số dư hiện hành."
    ),
    lots: scopedOperationalSource(
      lotsResult,
      selectedWarehouse.id,
      correlationId,
      "Không thể tải các lô tồn kho."
    ),
    transactions: scopedOperationalSource(
      transactionsResult,
      selectedWarehouse.id,
      correlationId,
      "Không thể tải sổ giao dịch kho."
    ),
    analytics,
    partial: [
      balancesResult,
      lotsResult,
      transactionsResult,
      analyticsResult
    ].some((result) => result.status === "rejected"),
    materials,
    masters,
    mastersAvailable
  };
}

async function loadInventoryMasters(
  context: InventoryReadContext,
  correlationId: string,
  materials: SourceResult<readonly Material[]>
): Promise<
  Readonly<{
    masters: SourceResult<InventoryMasters>;
    mastersAvailable: boolean;
  }>
> {
  const suppliersResult = await Promise.allSettled([
    getSupplierCatalog(context)
  ]).then(([result]) => result);
  if (suppliersResult.status === "fulfilled" && materials.status === "ready") {
    return {
      masters: {
        status: "ready",
        data: {
          suppliers: suppliersResult.value,
          materials: materials.data
        }
      },
      mastersAvailable: true
    };
  }
  return {
    masters: sourceFailure(
      correlationId,
      "Không thể tải danh mục nhà cung cấp và vật tư để lập biểu mẫu."
    ),
    mastersAvailable: false
  };
}

function scopedOperationalSource<
  Item extends Readonly<{ warehouseId: string }>
>(
  result: PromiseSettledResult<InventoryPage<Item>>,
  warehouseId: string,
  correlationId: string,
  message: string
): SourceResult<InventoryPage<Item>> {
  if (
    result.status === "fulfilled"
    && result.value.items.every((item) => item.warehouseId === warehouseId)
  ) {
    return { status: "ready", data: result.value };
  }
  return sourceFailure(correlationId, message);
}

function settledSource<T>(
  result: PromiseSettledResult<T>,
  correlationId: string,
  message: string
): SourceResult<T> {
  return result.status === "fulfilled"
    ? { status: "ready", data: result.value }
    : sourceFailure(correlationId, message);
}

function toOccurredFromInstant(date: string | undefined): string | undefined {
  return date ? `${date}T00:00:00Z` : undefined;
}

function toOccurredToInstant(date: string | undefined): string | undefined {
  return date ? `${date}T23:59:59.999Z` : undefined;
}

function sourceFailure(
  correlationId: string,
  message: string
): Readonly<{ status: "failed"; message: string; correlationId: string }> {
  return { status: "failed", message, correlationId };
}
