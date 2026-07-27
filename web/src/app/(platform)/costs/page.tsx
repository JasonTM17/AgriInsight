import { redirect } from "next/navigation";

import { StatePanel } from "@/components/app-shell/state-panels";
import { CostAnalysisPage } from "@/features/costs/components/cost-analysis-page";
import { parseCostFilterState } from "@/features/costs/cost-filter-schema";
import { loadCostViewModel } from "@/features/costs/load-cost-view-model";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";

export const dynamic = "force-dynamic";

type CostSearchParams = Record<
  string,
  string | readonly string[] | undefined
>;

export default async function CostsPage({
  searchParams
}: Readonly<{ searchParams: Promise<CostSearchParams> }>) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/costs");
  if (!context.identity.permissions.has("COST_READ")) {
    return (
      <StatePanel
        correlationId={context.correlationId}
        message="Phiên hiện tại không có quyền đọc dữ liệu chi phí."
        state="denied"
      />
    );
  }
  const filters = parseCostFilterState(await searchParams);
  if (!filters) {
    return (
      <StatePanel
        actionHref="/costs?lens=operating"
        actionLabel="Về lens vận hành"
        correlationId={context.correlationId}
        label="Bộ lọc chi phí chưa hợp lệ"
        message="Lens, UUID, ngày hoặc nhóm chi phí không đúng contract an toàn."
        state="failed"
      />
    );
  }
  let viewModel: Awaited<ReturnType<typeof loadCostViewModel>>;
  try {
    viewModel = await loadCostViewModel({
      accessToken: context.accessToken,
      correlationId: context.correlationId,
      env: context.env,
      filters
    });
  } catch {
    return (
      <StatePanel
        actionHref={`/costs?lens=${filters.lens}`}
        correlationId={context.correlationId}
        message="Không thể tải dữ liệu chi phí trong lần thử này."
        state="failed"
      />
    );
  }
  if (viewModel.kind === "foreign_farm") {
    return (
      <StatePanel
        actionHref={`/costs?lens=${filters.lens}`}
        correlationId={context.correlationId}
        message="Nông trại được yêu cầu không thuộc phạm vi chi phí hiện hành."
        state="denied"
      />
    );
  }
  if (viewModel.kind === "farm_scope_unavailable") {
    return (
      <StatePanel
        actionHref={`/costs?lens=${filters.lens}`}
        correlationId={context.correlationId}
        message="Không thể xác minh danh mục nông trại trước khi áp dụng bộ lọc."
        state="failed"
      />
    );
  }
  return (
    <CostAnalysisPage
      canManage={context.identity.permissions.has("COST_MANAGE")}
      viewModel={viewModel}
    />
  );
}
