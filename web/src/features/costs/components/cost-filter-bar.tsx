import Link from "next/link";

import {
  costAnalysisHref,
  type CostFilterState
} from "../cost-filter-schema";
import type {
  CostSourceResult
} from "../load-cost-view-model";
import type { OperationalFarm } from "@/features/overview/resolve-analytics-codes";
import styles from "./cost-analysis.module.css";

export function CostFilterBar({
  state,
  dateRange,
  farms
}: Readonly<{
  state: CostFilterState;
  dateRange: Readonly<{ from: string; to: string }>;
  farms: CostSourceResult<readonly OperationalFarm[]>;
}>) {
  const selectedFarm = state.filters.farmId ?? "";
  const procurementState: CostFilterState = {
    lens: "procurement",
    filters: {
      farmId: state.filters.farmId,
      from: state.filters.from,
      to: state.filters.to
    }
  };
  const operatingState: CostFilterState = {
    lens: "operating",
    filters: state.filters
  };
  return (
    <section aria-label="Bộ lọc phân tích chi phí" className={styles.filterCard}>
      <div className={styles.lensTabs} role="tablist" aria-label="Lens chi phí">
        <Link
          aria-selected={state.lens === "operating"}
          className={state.lens === "operating" ? styles.activeTab : styles.tab}
          href={costAnalysisHref(operatingState)}
          role="tab"
          prefetch={false}
        >
          Vận hành
          <small>Spring ledger</small>
        </Link>
        <Link
          aria-selected={state.lens === "procurement"}
          className={state.lens === "procurement" ? styles.activeTab : styles.tab}
          href={costAnalysisHref(procurementState)}
          role="tab"
          prefetch={false}
        >
          Mua hàng
          <small>Gold snapshot</small>
        </Link>
      </div>
      <form action="/costs" className={styles.filterForm} method="get">
        <input name="lens" type="hidden" value={state.lens} />
        <label>
          Từ ngày
          <input defaultValue={dateRange.from} name="from" type="date" />
        </label>
        <label>
          Đến ngày
          <input defaultValue={dateRange.to} name="to" type="date" />
        </label>
        <label>
          Nông trại
          <select defaultValue={selectedFarm} name="farmId">
            <option value="">Tất cả phạm vi được cấp quyền</option>
            {farms.status === "ready"
              ? farms.data.map((farm) => (
                  <option key={farm.id} value={farm.id}>
                    {farm.code} · {farm.displayName}
                  </option>
                ))
              : null}
          </select>
        </label>
        {state.lens === "operating" ? (
          <>
            <label>
              Nhóm chi phí
              <select defaultValue={state.filters.category ?? ""} name="category">
                <option value="">Tất cả nhóm</option>
                <option value="LABOR">Nhân công</option>
                <option value="MATERIAL">Vật tư</option>
                <option value="MACHINERY">Máy móc</option>
                <option value="TRANSPORT">Vận chuyển</option>
                <option value="UTILITY">Tiện ích</option>
                <option value="OTHER">Khác</option>
              </select>
            </label>
            <label>
              Mã mùa vụ (tuỳ chọn)
              <input
                defaultValue={state.filters.seasonId ?? ""}
                name="seasonId"
                pattern="[0-9a-fA-F-]{36}"
                placeholder="UUID mùa vụ"
              />
            </label>
            <label>
              Mã hoạt động (tuỳ chọn)
              <input
                defaultValue={state.filters.activityId ?? ""}
                name="activityId"
                pattern="[0-9a-fA-F-]{36}"
                placeholder="UUID hoạt động"
              />
            </label>
          </>
        ) : null}
        <div className={styles.filterActions}>
          <button className={styles.primaryButton} type="submit">Áp dụng phạm vi</button>
          <Link className={styles.secondaryButton} href={`/costs?lens=${state.lens}`} prefetch={false}>
            Xóa lọc
          </Link>
        </div>
      </form>
      <p className={styles.contractNote}>
        {state.lens === "operating"
          ? "Sổ vận hành chỉ nhận cửa sổ UTC tối đa 366 ngày; posting và reversal được giữ bất biến."
          : "Mua hàng là lens riêng, không cộng dồn vào chi phí vận hành hoặc giá trị tồn kho."}
      </p>
    </section>
  );
}
