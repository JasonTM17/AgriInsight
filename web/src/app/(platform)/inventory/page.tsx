import { redirect } from "next/navigation";

import { StatePanel } from "@/components/app-shell/state-panels";
import { InventoryControlPage } from "@/features/inventory/components/inventory-control-page";
import { loadInventoryViewModel } from "@/features/inventory/load-inventory-view-model";
import { parseInventoryRouteState } from "@/features/inventory/inventory-route-state";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";

export const dynamic = "force-dynamic";

type InventorySearchParams = Record<
  string,
  string | readonly string[] | undefined
>;

export default async function InventoryPage({
  searchParams
}: Readonly<{ searchParams: Promise<InventorySearchParams> }>) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/inventory");
  if (!context.identity.permissions.has("INVENTORY_READ")) {
    return (
      <StatePanel
        correlationId={context.correlationId}
        message="Phiên hiện tại không có quyền đọc tồn kho."
        state="denied"
      />
    );
  }
  const routeState = parseInventoryRouteState(await searchParams);
  if (!routeState) {
    return (
      <StatePanel
        actionHref="/inventory"
        actionLabel="Xóa bộ lọc"
        correlationId={context.correlationId}
        label="Liên kết tồn kho không hợp lệ"
        message="Kho, bộ lọc ngày hoặc offset không đúng định dạng an toàn."
        state="failed"
      />
    );
  }
  let viewModel: Awaited<ReturnType<typeof loadInventoryViewModel>>;
  try {
    viewModel = await loadInventoryViewModel({
      accessToken: context.accessToken,
      canManage: context.identity.permissions.has("INVENTORY_MANAGE"),
      correlationId: context.correlationId,
      env: context.env,
      state: routeState
    });
  } catch {
    return (
      <StatePanel
        actionHref="/inventory"
        correlationId={context.correlationId}
        message="Không thể xác minh danh mục kho ở lần thử này."
        state="failed"
      />
    );
  }
  if (viewModel.kind === "picker") {
    const warehouse = viewModel.warehouses[0];
    if (warehouse) redirect(`/inventory?warehouseId=${warehouse.id}`);
    return (
      <StatePanel
        correlationId={context.correlationId}
        message="Phiên hiện tại chưa được phân công kho hoạt động."
        state="denied"
      />
    );
  }
  if (viewModel.kind === "foreign_warehouse") {
    return (
      <StatePanel
        correlationId={context.correlationId}
        message="Kho được yêu cầu không thuộc phạm vi được phân công."
        state="denied"
      />
    );
  }
  if (viewModel.kind === "foreign_material") {
    return (
      <StatePanel
        actionHref={routeState.warehouseId
          ? `/inventory?warehouseId=${routeState.warehouseId}`
          : "/inventory"}
        correlationId={context.correlationId}
        message="Vật tư được yêu cầu không thuộc danh mục đã xác minh."
        state="failed"
      />
    );
  }
  return (
    <InventoryControlPage
      canManage={context.identity.permissions.has("INVENTORY_MANAGE")}
      routeState={routeState}
      viewModel={viewModel}
    />
  );
}
