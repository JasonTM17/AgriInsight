import { redirect } from "next/navigation";

import { StatePanel } from "@/components/app-shell/state-panels";
import { canAccessAdministration } from "@/features/admin/admin-access";
import { AdminReadError } from "@/features/admin/admin-resource-client";
import { loadAdminAudit } from "@/features/admin/admin-read-model";
import { parseAdminAuditState } from "@/features/admin/admin-route-state";
import { AdminAuditPage } from "@/features/admin/components/admin-audit-page";
import { AdminPageHeader } from "@/features/admin/components/admin-page-header";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";

export const dynamic = "force-dynamic";
type SearchParams = Record<string, string | readonly string[] | undefined>;

export default async function AdminAuditRoute({
  searchParams
}: Readonly<{ searchParams: Promise<SearchParams> }>) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/admin/audit");
  if (!canAccessAdministration(context.identity)) {
    return <StatePanel correlationId={context.correlationId} message="Phiên hiện tại không có quyền đọc nhật ký quản trị." state="denied" />;
  }
  const filters = parseAdminAuditState(await searchParams);
  if (!filters) {
    return <StatePanel actionHref="/admin/audit" actionLabel="Đặt lại bộ lọc" correlationId={context.correlationId} message="Bộ lọc kiểm toán không đúng contract an toàn." state="failed" />;
  }
  let audit: Awaited<ReturnType<typeof loadAdminAudit>>;
  try {
    audit = await loadAdminAudit(context, filters);
  } catch (error) {
    return adminAuditFailure(error, context.correlationId);
  }
  return <><AdminPageHeader active="audit" /><AdminAuditPage audit={audit} filters={filters} /></>;
}

function adminAuditFailure(error: unknown, correlationId: string) {
  const denied = error instanceof AdminReadError && error.kind === "denied";
  return <StatePanel actionHref={denied ? null : "/admin/audit"} correlationId={correlationId} message={denied ? "Dịch vụ từ chối phạm vi nhật ký của phiên hiện tại." : "Không thể tải nhật ký quản trị trong lần thử này."} state={denied ? "denied" : "failed"} />;
}
