import Link from "next/link";

import { StatePanel } from "@/components/app-shell/state-panels";
import type { FarmListViewModel } from "@/features/farms/load-farm-intelligence-view-model";
import { toFilterQuery } from "@/features/overview/overview-filter-schema";
import {
  AnalyticsContextLine,
  LineageBanner
} from "@/features/overview/components/lineage-banner";
import styles from "@/features/overview/components/overview-farms.module.css";

const currency = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0
});
const number = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 });

export function FarmList({ viewModel }: { viewModel: FarmListViewModel }) {
  return (
    <div className={styles.stack}>
      <header className={styles.pageIntro}>
        <div>
          <p className="eyebrow">Hiệu quả theo phạm vi được cấp quyền</p>
          <h2>Hiệu quả nông trại</h2>
          <p>So sánh hiệu quả đã đối soát và mở từng nông trại để xem chi tiết.</p>
        </div>
      </header>
      <FarmFilters viewModel={viewModel} />
      {viewModel.partial && viewModel.farms.status === "ready" ? (
        <StatePanel
          actionHref={currentFarmHref(viewModel)}
          label="Chỉ số phân tích tạm gián đoạn"
          message="Danh mục nông trại vẫn hiển thị, nhưng một số chỉ số chưa được đồng bộ ở lần tải này."
          state="partial"
        />
      ) : null}
      {viewModel.analyticsMetadata ? <LineageBanner envelope={viewModel.analyticsMetadata} /> : null}
      {viewModel.farms.status === "failed" ? (
        <StatePanel
          actionHref={currentFarmHref(viewModel)}
          correlationId={viewModel.farms.correlationId}
          message="Không thể xác minh danh mục nông trại trong phạm vi hiện tại."
          state="failed"
        />
      ) : viewModel.farms.data.length === 0 ? (
        <StatePanel
          actionHref="/farms"
          actionLabel="Xóa bộ lọc"
          label="Không có kết quả"
          message="Không có nông trại phù hợp với bộ lọc hiện tại."
          state="empty"
        />
      ) : (
        <section className={styles.farmTable}>
          <div className={styles.sectionHeading}>
            <div>
              <p className="eyebrow">Kết quả đã lọc</p>
              <h3>{viewModel.pagination.totalItems} nông trại</h3>
              {viewModel.analyticsMetadata ? (
                <AnalyticsContextLine envelope={viewModel.analyticsMetadata} period="Kỳ hiệu quả hiện hành" />
              ) : null}
            </div>
            <span>Trang {viewModel.pagination.page}/{viewModel.pagination.totalPages}</span>
          </div>
          <div className={styles.tableScroll} role="region" aria-label="Danh sách nông trại có thể cuộn" tabIndex={0}>
            <table>
              <caption className="sr-only">Danh sách nông trại và chỉ số Gold đã ghép theo mã chuẩn</caption>
              <thead>
                <tr><th>Nông trại</th><th>Mã</th><th>Trạng thái</th><th>Lợi nhuận</th><th>Biên lợi nhuận</th><th>Sản lượng/ha</th></tr>
              </thead>
              <tbody>
                {viewModel.farms.data.map(({ farm, analytics }) => (
                  <tr key={farm.id}>
                    <td data-label="Nông trại">
                      {farm.active ? (
                        <Link className={styles.farmLink} href={farmHref(farm.id, viewModel)}>{farm.displayName}</Link>
                      ) : (
                        <span>{farm.displayName}</span>
                      )}
                    </td>
                    <td data-label="Mã" translate="no">{farm.code}</td>
                    <td data-label="Trạng thái">{farm.active ? "Đang hoạt động" : "Ngừng hoạt động · chỉ xem trong danh sách"}</td>
                    <td data-label="Lợi nhuận">{analytics ? currency.format(analytics.profitVnd) : <span className={styles.mutedValue}>Chưa đồng bộ</span>}</td>
                    <td data-label="Biên lợi nhuận">{analytics ? `${number.format(analytics.profitMarginPct)}%` : "—"}</td>
                    <td data-label="Sản lượng/ha">{analytics ? `${number.format(analytics.yieldKgPerHa)} kg/ha` : "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <FarmPagination viewModel={viewModel} />
        </section>
      )}
    </div>
  );
}

function farmHref(farmId: string, viewModel: FarmListViewModel): string {
  const query = toFilterQuery(viewModel.filters);
  return `/farms/${farmId}${query.size > 0 ? `?${query}` : ""}`;
}

function currentFarmHref(viewModel: FarmListViewModel): string {
  const query = toFilterQuery(viewModel.filters);
  return `/farms${query.size > 0 ? `?${query}` : ""}`;
}

function FarmFilters({ viewModel }: { viewModel: FarmListViewModel }) {
  return (
    <form action="/farms" className={styles.filterForm} method="get">
      <label>
        Tìm theo tên hoặc mã
        <input autoComplete="off" defaultValue={viewModel.filters.search} maxLength={80} name="search" placeholder="Ví dụ: An Phú…" spellCheck={false} type="search" />
      </label>
      <label>
        Trạng thái
        <select defaultValue={viewModel.filters.status} name="status">
          <option value="active">Đang hoạt động</option>
          <option value="inactive">Ngừng hoạt động</option>
          <option value="all">Tất cả</option>
        </select>
      </label>
      <label>
        Sắp xếp
        <select defaultValue={viewModel.filters.sort} name="sort">
          <option value="farm_code">Mã nông trại</option>
          <option value="profit_desc">Lợi nhuận giảm dần</option>
        </select>
      </label>
      <button type="submit">Áp dụng</button>
      <Link className={styles.resetLink} href="/farms">Xóa bộ lọc</Link>
    </form>
  );
}

function FarmPagination({ viewModel }: { viewModel: FarmListViewModel }) {
  const { page, totalPages } = viewModel.pagination;
  if (totalPages <= 1) return null;
  return (
    <nav aria-label="Phân trang nông trại" className={styles.pagination}>
      {page > 1 ? (
        <Link href={farmPageHref(viewModel, page - 1)}>Trang trước</Link>
      ) : <span aria-hidden="true" className={styles.paginationSpacer} />}
      <span className={styles.paginationCurrent}>Trang {page} trên {totalPages}</span>
      {page < totalPages ? (
        <Link href={farmPageHref(viewModel, page + 1)}>Trang sau</Link>
      ) : <span aria-hidden="true" className={styles.paginationSpacer} />}
    </nav>
  );
}

function farmPageHref(viewModel: FarmListViewModel, page: number): string {
  const query = toFilterQuery(viewModel.filters, { page });
  return `/farms${query.size > 0 ? `?${query}` : ""}`;
}
