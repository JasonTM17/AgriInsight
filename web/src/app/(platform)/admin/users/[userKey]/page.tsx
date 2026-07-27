import { forbidden, redirect } from "next/navigation";
import { z } from "zod";

import { StatePanel } from "@/components/app-shell/state-panels";
import {
  canAccessAdministration,
  getAdminCapabilities
} from "@/features/admin/admin-access";
import { AdminReadError } from "@/features/admin/admin-resource-client";
import { loadAdminSubject } from "@/features/admin/admin-read-model";
import { AdminPageHeader } from "@/features/admin/components/admin-page-header";
import { AdminSubjectPage } from "@/features/admin/components/admin-subject-page";
import { loadPlatformPageContext } from "@/features/overview/load-platform-page-context";

export const dynamic = "force-dynamic";

export default async function AdminUserPage({
  params
}: Readonly<{ params: Promise<{ userKey: string }> }>) {
  const context = await loadPlatformPageContext();
  if (!context) redirect("/login?returnTo=/admin");
  if (!canAccessAdministration(context.identity)) forbidden();
  const userKey = z.uuid().safeParse((await params).userKey);
  if (!userKey.success) {
    return <StatePanel actionHref="/admin" actionLabel="Về danh sách" correlationId={context.correlationId} message="Mã hồ sơ không đúng định dạng UUID." state="failed" />;
  }
  let subject: Awaited<ReturnType<typeof loadAdminSubject>>;
  try {
    subject = await loadAdminSubject(context, userKey.data);
  } catch (error) {
    return adminSubjectFailure(error, context.correlationId, userKey.data);
  }
  return (
    <>
      <AdminPageHeader active="directory" />
      <AdminSubjectPage capabilities={getAdminCapabilities(context.identity)} subject={subject} />
    </>
  );
}

function adminSubjectFailure(
  error: unknown,
  correlationId: string,
  userKey: string
) {
  if (error instanceof AdminReadError && error.kind === "not_found") {
    return <StatePanel actionHref="/admin" actionLabel="Về danh sách" correlationId={correlationId} message="Hồ sơ không tồn tại trong tenant hiện tại." state="empty" />;
  }
  if (error instanceof AdminReadError && error.kind === "denied") forbidden();
  return <StatePanel actionHref={`/admin/users/${userKey}`} correlationId={correlationId} message="Không thể tải hồ sơ quản trị trong lần thử này." state="failed" />;
}
