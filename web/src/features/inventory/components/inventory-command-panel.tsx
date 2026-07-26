import type {
  Material,
  StockLot,
  Supplier,
  InventoryTransaction,
  Warehouse
} from "../inventory-generated-client-adapter";
import { InventoryReversalForm } from "./inventory-reversal-form";
import { InventoryTransactionForm } from "./inventory-transaction-form";
import styles from "./inventory-control.module.css";

export function InventoryCommandPanel({
  lots,
  materials,
  suppliers,
  transactions,
  warehouse
}: Readonly<{
  lots: readonly StockLot[];
  materials: readonly Material[];
  suppliers: readonly Supplier[];
  transactions: readonly InventoryTransaction[];
  warehouse: Warehouse;
}>) {
  return (
    <section className={styles.commandStack} aria-labelledby="command-title">
      <div className={styles.sectionHeading}>
        <div>
          <p className="eyebrow">Inventory manager</p>
          <h2 id="command-title">Ghi nhận nghiệp vụ có kiểm soát</h2>
          <p>Phiếu được gửi trực tiếp vào ledger tenant-scoped với Idempotency-Key và audit reason code.</p>
        </div>
        <span className={styles.sourceBadge}>{warehouse.code} · quyền ghi</span>
      </div>
      <div className={styles.commandGrid}>
        <details className={styles.commandCard} open>
          <summary>Nhập / xuất kho</summary>
          <InventoryTransactionForm
            lots={lots}
            materials={materials}
            suppliers={suppliers}
            warehouse={warehouse}
          />
        </details>
        <details className={styles.commandCard}>
          <summary>Đảo một phần giao dịch</summary>
          <InventoryReversalForm
            materials={materials}
            transactions={transactions}
          />
        </details>
      </div>
    </section>
  );
}
