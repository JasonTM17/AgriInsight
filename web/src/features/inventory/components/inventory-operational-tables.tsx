import Link from "next/link";
import type { ReactNode } from "react";

import type {
  InventoryTransaction,
  Material,
  StockBalance,
  StockLot
} from "../inventory-generated-client-adapter";
import { INVENTORY_MAX_OFFSET } from "../inventory-generated-client-adapter";
import type { InventoryRouteState } from "../inventory-route-state";
import type { SourceResult } from "../load-inventory-view-model";
import { inventoryHref } from "../inventory-route-state";
import {
  formatCurrency,
  formatDate,
  formatDateTime,
  formatQuantity
} from "../inventory-format";
import styles from "./inventory-control.module.css";

export function InventoryOperationalTables({
  balances,
  lots,
  materials,
  routeState,
  transactions
}: Readonly<{
  balances: SourceResult<{ items: readonly StockBalance[]; hasMore: boolean; limit: number; offset: number }>;
  lots: SourceResult<{ items: readonly StockLot[]; hasMore: boolean; limit: number; offset: number }>;
  materials: readonly Material[] | null;
  routeState: InventoryRouteState;
  transactions: SourceResult<{ items: readonly InventoryTransaction[]; hasMore: boolean; limit: number; offset: number }>;
}>) {
  const materialNames = new Map(
    materials?.map((material) => [material.id, `${material.code} · ${material.displayName}`]) ?? []
  );
  return (
    <section className={styles.operationalStack} aria-labelledby="operational-title">
      <div className={styles.sectionHeading}>
        <div>
          <p className="eyebrow">Spring operational ledger</p>
          <h2 id="operational-title">Bằng chứng vận hành hiện hành</h2>
          <p>Thứ tự lô và giao dịch là thứ tự authoritative từ backend; giao diện không sắp xếp lại.</p>
        </div>
        <span className={styles.sourceBadge}>RLS + tenant scope</span>
      </div>
      <div className={styles.operationalGrid}>
        <ReadPanel
          pager={<Pager page={balances} routeState={routeState} offsetKey="balanceOffset" />}
          result={balances}
          title="Số dư vật tư"
        >
          {(page) => (
            <div className={styles.tableScroll}>
              <table className={styles.dataTable} data-testid="inventory-balance-table">
                <thead><tr><th>Vật tư</th><th>Số dư</th><th>Ngưỡng</th><th>Giá trị</th></tr></thead>
                <tbody>
                  {page.items.map((balance) => (
                    <tr key={balance.id}>
                      <th>{balance.materialCode}<small>{balance.materialName}</small></th>
                      <td>{formatQuantity(balance.quantityOnHand, balance.unit)}</td>
                      <td><span className={balance.lowStock ? styles.warningText : styles.okText}>{balance.lowStock ? "Dưới ngưỡng" : "Ổn định"}</span></td>
                      <td>{formatCurrency(balance.inventoryValueVnd)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </ReadPanel>
        <ReadPanel
          pager={<Pager page={lots} routeState={routeState} offsetKey="lotOffset" />}
          result={lots}
          title="Lô tồn kho · FEFO server"
        >
          {(page) => (
            <div className={styles.tableScroll}>
              <table className={styles.dataTable} data-testid="inventory-lot-table">
                <thead><tr><th>Lô</th><th>Vật tư</th><th>Còn lại</th><th>Hạn</th></tr></thead>
                <tbody>
                  {page.items.map((lot) => (
                    <tr key={lot.id}>
                      <th>{lot.batchCode}<small>{lot.supplierCode}</small></th>
                      <td>{lot.materialCode}</td>
                      <td>{formatQuantity(lot.availableQuantity, lot.unit)}</td>
                      <td><span className={lot.expired || lot.expiringSoon ? styles.warningText : styles.okText}>{formatDate(lot.expiryDate)}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </ReadPanel>
      </div>
      <ReadPanel
        pager={<Pager page={transactions} routeState={routeState} offsetKey="txOffset" />}
        result={transactions}
        title="Sổ giao dịch bất biến"
      >
        {(page) => (
          <div className={styles.tableScroll}>
            <table className={styles.dataTable} data-testid="inventory-transaction-table">
              <thead><tr><th>Thời điểm</th><th>Loại</th><th>Vật tư</th><th>Số lượng</th><th>Tham chiếu</th><th>Phiên bản</th></tr></thead>
              <tbody>
                {page.items.map((transaction) => (
                  <tr data-testid="inventory-transaction-row" key={transaction.id}>
                    <th>{formatDateTime(transaction.occurredAt)}<small>{transaction.id}</small></th>
                    <td><span className={styles.statusPill}>{transaction.kind}</span></td>
                    <td>{materialNames.get(transaction.materialId) ?? transaction.materialId}</td>
                    <td>{formatQuantity(transaction.signedQuantityEffect, transaction.unit)}</td>
                    <td>{transaction.referenceCode ?? transaction.reason ?? "—"}</td>
                    <td>v{transaction.version}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </ReadPanel>
    </section>
  );
}

type OperationalPage<Item> = Readonly<{
  items: readonly Item[];
  hasMore: boolean;
  limit: number;
  offset: number;
}>;

function ReadPanel<Item>({
  children,
  pager,
  result,
  title
}: Readonly<{
  children: (page: OperationalPage<Item>) => ReactNode;
  pager: ReactNode;
  result: SourceResult<OperationalPage<Item>>;
  title: string;
}>) {
  return (
    <article className={styles.panel}>
      <div className={styles.panelHeading}><h3>{title}</h3></div>
      {result.status === "failed" ? (
        <p className={styles.inlineError}>{result.message}</p>
      ) : result.data.items.length === 0 ? (
        <p className={styles.emptyState}>Không có dữ liệu trong trang này.</p>
      ) : (
        children(result.data)
      )}
      {pager}
    </article>
  );
}

function Pager({
  offsetKey,
  page,
  routeState
}: Readonly<{
  offsetKey: "balanceOffset" | "lotOffset" | "txOffset";
  page: SourceResult<OperationalPage<unknown>>;
  routeState: InventoryRouteState;
}>) {
  if (page.status === "failed" || (!page.data.hasMore && page.data.offset === 0)) return null;
  const previousOffset = Math.max(0, page.data.offset - page.data.limit);
  const nextOffset = page.data.offset + page.data.limit;
  const canLoadNext =
    page.data.hasMore && nextOffset <= INVENTORY_MAX_OFFSET;
  return (
    <nav className={styles.pagination} aria-label="Phân trang dữ liệu kho">
      {page.data.offset > 0 ? (
        <Link href={inventoryHref(routeState, { [offsetKey]: previousOffset })}>← Trước</Link>
      ) : <span />}
      <span>Trang bắt đầu {page.data.offset + 1}</span>
      {canLoadNext ? (
        <Link href={inventoryHref(routeState, { [offsetKey]: nextOffset })}>Sau →</Link>
      ) : page.data.hasMore ? (
        <span>Đã đạt giới hạn máy chủ</span>
      ) : <span />}
    </nav>
  );
}
