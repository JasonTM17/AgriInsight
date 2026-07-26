import type { ReactNode } from "react";

import { StatePanel } from "@/components/app-shell/state-panels";

import type { InventoryRouteState } from "../inventory-route-state";
import type { InventoryViewModel } from "../load-inventory-view-model";
import { InventoryAnalyticsPanels } from "./inventory-analytics-panels";
import { InventoryCommandPanel } from "./inventory-command-panel";
import { InventoryFilterBar } from "./inventory-filter-bar";
import { InventoryOperationalTables } from "./inventory-operational-tables";
import styles from "./inventory-control.module.css";

export function InventoryControlPage({
  canManage,
  routeState,
  viewModel
}: Readonly<{
  canManage: boolean;
  routeState: InventoryRouteState;
  viewModel: Extract<InventoryViewModel, { kind: "ready" }>;
}>) {
  const materials =
    viewModel.materials.status === "ready" ? viewModel.materials.data : null;
  const lots =
    viewModel.lots.status === "ready" ? viewModel.lots.data.items : [];
  const transactions =
    viewModel.transactions.status === "ready"
      ? viewModel.transactions.data.items
      : [];
  const mastersReady =
    viewModel.masters?.status === "ready" && viewModel.mastersAvailable;
  const hasOperationalFilters = Boolean(
    routeState.filters.materialId
    || routeState.filters.lowStock
    || routeState.filters.kind
    || routeState.filters.from
    || routeState.filters.to
  );
  return (
    <div className={styles.page} data-testid="inventory-control-page">
      <header className={styles.pageHeading}>
        <div>
          <p className="eyebrow">Warehouse control ledger</p>
          <h1>Kiểm soát tồn kho</h1>
          <p>
            {viewModel.selectedWarehouse.code} · {viewModel.selectedWarehouse.displayName}
            {" "}— phạm vi được backend xác nhận cho phiên hiện tại.
          </p>
        </div>
        <div className={styles.trustNote}>
          <strong>Spring operational + Gold intelligence</strong>
          <span>Ledger mới và snapshot analytics được hiển thị như hai nguồn có lineage riêng.</span>
        </div>
      </header>
      <InventoryFilterBar
        materials={materials}
        routeState={routeState}
        warehouses={viewModel.warehouses}
      />
      <InventoryAnalyticsPanels
        analytics={viewModel.analytics}
        hasOperationalFilters={hasOperationalFilters}
        selectedWarehouseCode={viewModel.selectedWarehouse.code}
      />
      <InventoryOperationalTables
        balances={viewModel.balances}
        lots={viewModel.lots}
        materials={materials}
        routeState={routeState}
        transactions={viewModel.transactions}
      />
      {canManage && mastersReady ? (
        <InventoryCommandPanel
          lots={lots}
          materials={viewModel.masters.data.materials}
          suppliers={viewModel.masters.data.suppliers}
          transactions={transactions}
          warehouse={viewModel.selectedWarehouse}
        />
      ) : canManage ? (
        <section className={styles.degradedPanel} aria-labelledby="command-unavailable-title">
          <p className="eyebrow">Inventory manager</p>
          <h2 id="command-unavailable-title">Biểu mẫu ghi nhận đang khóa</h2>
          <p>Danh mục vật tư hoặc nhà cung cấp chưa được xác minh đầy đủ; dữ liệu đọc vẫn hoạt động.</p>
        </section>
      ) : null}
      {viewModel.partial ? (
        <p className={styles.partialNote} role="status">
          Một nguồn dữ liệu đang gián đoạn; mỗi panel giữ nguyên trạng thái nguồn để tránh suy diễn.
        </p>
      ) : null}
    </div>
  );
}

export function InventoryUnavailable({
  children,
  correlationId,
  message,
  state
}: Readonly<{
  children?: ReactNode;
  correlationId: string;
  message: string;
  state: "denied" | "failed";
}>) {
  return (
    <>
      {children}
      <StatePanel correlationId={correlationId} message={message} state={state} />
    </>
  );
}
