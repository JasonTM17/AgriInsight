import { randomUUID } from "node:crypto";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";

import { getAuthorizationContext } from "@/server/auth/authorization-context";
import { SESSION_COOKIE_NAME } from "@/server/auth/cookie-policy";
import { getAuthRuntime } from "@/server/auth/runtime";
import { AppShell } from "@/components/app-shell/app-shell";
import { StatePanel } from "@/components/app-shell/state-panels";
import { Icon } from "@/components/ui/icon";

export const dynamic = "force-dynamic";

export default async function ProtectedPage({
  searchParams
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const runtime = getAuthRuntime();
  const sessionToken = (await cookies()).get(SESSION_COOKIE_NAME)?.value;
  const state = await loadProtectedState(runtime, sessionToken);
  if (!state) redirect("/login?returnTo=/protected");
  const { identity, session } = state;
  const params = await searchParams;
  const moduleName = typeof params.module === "string" ? params.module : undefined;
  const moduleLabel = moduleName ? getModuleLabel(moduleName) : undefined;
  return (
    <AppShell
      identity={identity}
      searchParams={{ module: moduleName }}
    >
      <section className="route-page">
        <div className="route-page__intro">
          <div>
            <p className="eyebrow">Dữ liệu theo phạm vi được xác minh</p>
            <h2>{moduleLabel ?? "Chào " + identity.displayName}</h2>
            <p className="route-page__subtitle">
              {moduleLabel
                ? "Khu vực này đang chờ route nghiệp vụ liên kết với BFF có kiểm soát."
                : "Bắt đầu từ những tín hiệu quan trọng nhất của tenant và mùa vụ hiện hành."}
            </p>
          </div>
          <span className="freshness-badge"><span aria-hidden="true" className="status-dot" /> Phiên máy chủ hợp lệ</span>
        </div>
        {moduleLabel ? (
          <StatePanel state="partial" actionHref="/protected" />
        ) : (
          <>
            <div className="overview-grid">
              <article className="overview-card overview-card--primary">
                <div className="overview-card__heading">
                  <span className="overview-card__icon"><Icon name="grid" size={20} /></span>
                  <span className="eyebrow">Phạm vi truy cập</span>
                </div>
                <strong>{identity.permissions.size} quyền hiện hành</strong>
                <p>Hiển thị từ authorization context mới nhất của máy chủ Spring.</p>
              </article>
              <article className="overview-card">
                <div className="overview-card__heading">
                  <span className="overview-card__icon"><Icon name="shield-check" size={20} /></span>
                  <span className="eyebrow">Phiên</span>
                </div>
                <strong>Phiên v{session.sessionVersion}</strong>
                <p>Token không xuất hiện trong HTML hoặc JavaScript phía trình duyệt.</p>
              </article>
            </div>
            <div className="identity-strip">
              <div><span>Tenant</span><strong translate="no">{identity.tenantCode}</strong></div>
              <div><span>Email</span><strong>{identity.email ?? "Chưa cung cấp"}</strong></div>
              <div><span>Vai trò</span><strong>{identity.roles.size} vai trò</strong></div>
            </div>
          </>
        )}
      </section>
    </AppShell>
  );
}

function getModuleLabel(moduleName: string): string | undefined {
  const labels: Record<string, string> = {
    farms: "Nông trại",
    work: "Công việc",
    inventory: "Tồn kho",
    costs: "Chi phí",
    "crop-health": "Sức khỏe cây trồng",
    "data-quality": "Chất lượng dữ liệu",
    administration: "Quản trị"
  };
  return labels[moduleName];
}

async function loadProtectedState(
  runtime: ReturnType<typeof getAuthRuntime>,
  sessionToken: string | undefined
) {
  try {
    const session = await runtime.auth.requireSession(sessionToken);
    const identity = await getAuthorizationContext(
      runtime.env,
      session.accessToken,
      randomUUID()
    );
    return { identity, session };
  } catch {
    return null;
  }
}
