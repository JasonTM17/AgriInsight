import { randomUUID } from "node:crypto";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";

import { getAuthorizationContext } from "@/server/auth/authorization-context";
import { SESSION_COOKIE_NAME } from "@/server/auth/cookie-policy";
import { getAuthRuntime } from "@/server/auth/runtime";

export const dynamic = "force-dynamic";

export default async function ProtectedPage() {
  const runtime = getAuthRuntime();
  const sessionToken = (await cookies()).get(SESSION_COOKIE_NAME)?.value;
  const state = await loadProtectedState(runtime, sessionToken);
  if (!state) redirect("/login?returnTo=/protected");
  const { identity, session } = state;
  return (
    <section className="foundation-panel">
      <p className="eyebrow">Phiên đã được xác minh</p>
      <h1>Chào {identity.displayName}</h1>
      <dl className="identity-grid">
        <div>
          <dt>Doanh nghiệp</dt>
          <dd>{identity.tenantCode}</dd>
        </div>
        <div>
          <dt>Email</dt>
          <dd>{identity.email}</dd>
        </div>
        <div>
          <dt>Quyền hiện hành</dt>
          <dd>{identity.permissions.size}</dd>
        </div>
        <div>
          <dt>Phiên</dt>
          <dd>v{session.sessionVersion}</dd>
        </div>
      </dl>
      <p className="muted">
        Trang này không render access token, refresh token hoặc chẩn đoán nhà
        cung cấp.
      </p>
    </section>
  );
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
