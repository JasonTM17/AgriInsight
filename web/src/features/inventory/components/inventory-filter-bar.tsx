import Link from "next/link";

import type {
  Material,
  Warehouse
} from "../inventory-generated-client-adapter";
import {
  inventoryHref,
  type InventoryRouteState
} from "../inventory-route-state";
import styles from "./inventory-control.module.css";

export function InventoryFilterBar({
  materials,
  routeState,
  warehouses
}: Readonly<{
  materials: readonly Material[] | null;
  routeState: InventoryRouteState;
  warehouses: readonly Warehouse[];
}>) {
  return (
    <form action="/inventory" className={styles.filterBar} method="get">
      <label>
        Kho được phân công
        <select defaultValue={routeState.warehouseId ?? ""} name="warehouseId" required>
          <option disabled value="">Chọn kho</option>
          {warehouses.map((warehouse) => (
            <option key={warehouse.id} value={warehouse.id}>
              {warehouse.code} · {warehouse.displayName}
            </option>
          ))}
        </select>
      </label>
      <label>
        Vật tư
        <select defaultValue={routeState.filters.materialId ?? ""} name="materialId">
          <option value="">Tất cả vật tư</option>
          {materials?.map((material) => (
            <option key={material.id} value={material.id}>
              {material.code} · {material.displayName}
            </option>
          ))}
        </select>
      </label>
      <label>
        Loại giao dịch
        <select defaultValue={routeState.filters.kind ?? ""} name="kind">
          <option value="">Tất cả</option>
          <option value="RECEIPT">Nhập kho</option>
          <option value="ISSUE">Xuất kho</option>
          <option value="REVERSAL">Đảo</option>
        </select>
      </label>
      <label>
        Từ ngày
        <input defaultValue={routeState.filters.from} name="from" type="date" />
      </label>
      <label>
        Đến ngày
        <input defaultValue={routeState.filters.to} name="to" type="date" />
      </label>
      <label className={styles.checkboxField}>
        <input
          defaultChecked={routeState.filters.lowStock === true}
          name="lowStock"
          type="checkbox"
          value="true"
        />
        Chỉ số dư thấp
      </label>
      <div className={styles.filterActions}>
        <button className={styles.primaryButton} type="submit">Áp dụng</button>
        {routeState.warehouseId ? (
          <Link
            className={styles.quietLink}
            href={inventoryHref(
              { ...routeState, filters: { ...routeState.filters, materialId: undefined, lowStock: undefined, kind: undefined, from: undefined, to: undefined }, balanceOffset: 0, lotOffset: 0, txOffset: 0 },
              { warehouseId: routeState.warehouseId, materialId: undefined, lowStock: false, kind: undefined, from: undefined, to: undefined, balanceOffset: 0, lotOffset: 0, txOffset: 0 }
            )}
          >
            Xóa lọc
          </Link>
        ) : null}
      </div>
    </form>
  );
}
