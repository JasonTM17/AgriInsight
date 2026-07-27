import { forbidden, redirect } from "next/navigation";

import { StatePanel } from "@/components/app-shell/state-panels";
import { canAccessAdministration } from "@/features/admin/admin-access";
import { AdminReadError } from "@/features/admin/admin-resource-client";
import { loadAdminDirectory } from "@/features/admin/admin-read-model";
import { parseAdminDirectoryState } from "@/features/admin/admin-route-state";
import { AdminDirectoryPage } from "@/features/admin/components/admin-directory-page";
import { AdminPageHeader } from "@/features/admin/components/admin-page-header";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";

export const dynamic = "force-dynamic";

type SearchParams = Record<string, string | readonly string[] | undefined>;

export default async function AdminPage({
  searchParams
}: Readonly<{ searchParams: Promise<SearchParams> }>) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/admin");
  if (!canAccessAdministration(context.identity)) forbidden();
  const filters = parseAdminDirectoryState(await searchParams);
  if (!filters) {
    return <StatePanel actionHref="/admin" actionLabel="Đặt lại bộ lọc" correlationId={context.correlationId} message="Bộ lọc người dùng không đúng contract an toàn." state="failed" />;
  }
  let directory: Awaited<ReturnType<typeof loadAdminDirectory>>;
  try {
    directory = await loadAdminDirectory(context, filters);
  } catch (error) {
    return adminReadFailure(error, context.correlationId);
  }
  return (
    <>
      <AdminPageHeader active="directory" />
      <AdminDirectoryPage directory={directory} search={filters.search} status={filters.status} />
    </>
  );
}

function adminReadFailure(error: unknown, correlationId: string) {
  if (error instanceof AdminReadError && error.kind === "denied") forbidden();
  return <StatePanel actionHref="/admin" correlationId={correlationId} message="Không thể tải danh mục quản trị trong lần thử này." state="failed" />;
}
