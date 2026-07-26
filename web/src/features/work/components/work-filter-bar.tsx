import type { WorkRouteFilters } from "../work-route-state";

import styles from "./work-operations.module.css";

export function WorkFilterBar({
  filters
}: Readonly<{ filters: WorkRouteFilters }>) {
  return (
    <form action="/work" className={styles.filterBar} method="get">
      <label>
        <span>Tìm công việc</span>
        <input
          defaultValue={filters.search}
          maxLength={100}
          name="search"
          placeholder="Mã hoặc tiêu đề"
          type="search"
        />
      </label>
      <label>
        <span>Trạng thái</span>
        <select defaultValue={filters.status ?? ""} name="status">
          <option value="">Tất cả trạng thái</option>
          <option value="PLANNED">Đã lên lịch</option>
          <option value="STARTED">Đang thực hiện</option>
          <option value="COMPLETED">Đã hoàn tất</option>
          <option value="CANCELLED">Đã hủy</option>
        </select>
      </label>
      <button className={styles.filterButton} type="submit">
        Áp dụng
      </button>
    </form>
  );
}
