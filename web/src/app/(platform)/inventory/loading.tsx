import styles from "@/features/inventory/components/inventory-control.module.css";

export default function InventoryLoading() {
  return (
    <div className={styles.page} aria-busy="true">
      <div className={styles.degradedPanel}>
        <p className="eyebrow">Warehouse control ledger</p>
        <h2>Đang xác minh phạm vi kho…</h2>
        <p>Đang tải danh mục được phân công và snapshot analytics.</p>
      </div>
    </div>
  );
}
