import { StatePanel } from "@/components/app-shell/state-panels";

import type { CostViewModel } from "../load-cost-view-model";
import { CostCommandPanel } from "./cost-command-panel";
import { CostFilterBar } from "./cost-filter-bar";
import { CostOperatingPanel } from "./cost-operating-panel";
import { CostProcurementPanel } from "./cost-procurement-panel";
import styles from "./cost-analysis.module.css";

export function CostAnalysisPage({
  canManage,
  viewModel
}: Readonly<{
  canManage: boolean;
  viewModel: Extract<CostViewModel, { kind: "ready" }>;
}>) {
  return (
    <div className={styles.page} data-testid="cost-analysis-page">
      <header className={styles.pageHeading}>
        <div>
          <p className="eyebrow">Cost intelligence / dual lens</p>
          <h1>Phân tích chi phí</h1>
          <p>
            Đối chiếu chi phí vận hành từ ledger Spring với chi phí mua hàng từ snapshot Gold,
            giữ lineage và scope rõ ràng ở từng nguồn.
          </p>
        </div>
        <div className={styles.trustNote}>
          <strong>Không trộn lens</strong>
          <span>Operating ledger, procurement detail và inventory value luôn là ba phạm vi riêng.</span>
        </div>
      </header>
      <CostFilterBar
        dateRange={viewModel.dateRange}
        farms={viewModel.farms}
        state={viewModel.filters}
      />
      <section className={styles.sourceBanner} aria-label="Nguồn dữ liệu chi phí">
        <span className={styles.sourceBadge}>
          {viewModel.filters.lens === "operating" ? "SPRING OPERATIONAL" : "FASTAPI GOLD SNAPSHOT"}
        </span>
        <span>
          {viewModel.selectedFarm
            ? `Phạm vi nông trại: ${viewModel.selectedFarm.code} · ${viewModel.selectedFarm.displayName}`
            : "Phạm vi tenant và các nông trại đã được xác minh"}
        </span>
        <span>{viewModel.dateRange.from} → {viewModel.dateRange.to} · UTC</span>
      </section>
      {viewModel.filters.lens === "operating" && viewModel.operating ? (
        <CostOperatingPanel
          dateRange={viewModel.dateRange}
          farmCode={viewModel.selectedFarm?.code}
          source={viewModel.operating}
        />
      ) : null}
      {viewModel.filters.lens === "procurement" && viewModel.procurement ? (
        <CostProcurementPanel
          dateRange={viewModel.dateRange}
          farmCode={viewModel.selectedFarm?.code}
          source={viewModel.procurement}
        />
      ) : null}
      {canManage && viewModel.filters.lens === "operating" && viewModel.operating?.status === "ready" ? (
        <CostCommandPanel entries={viewModel.operating.data.page.items} />
      ) : null}
      {viewModel.partial ? (
        <StatePanel
          actionHref={`/costs?lens=${viewModel.filters.lens}`}
          correlationId={viewModel.farms.status === "failed"
            ? viewModel.farms.correlationId
            : undefined}
          label="Cost view một phần"
          message="Một nguồn phụ trợ đang gián đoạn; số liệu chính vẫn giữ trạng thái nguồn riêng để tránh suy diễn."
          state="partial"
        />
      ) : null}
    </div>
  );
}
