import { cookies, headers } from "next/headers";
import { redirect } from "next/navigation";

import { SESSION_COOKIE_NAME } from "../../cookie-policy";
import { getAuthRuntime } from "../../runtime";

export const dynamic = "force-dynamic";

export default async function ProtectedPage() {
  const runtime = getAuthRuntime();
  const requestHeaders = await headers();
  if (requestHeaders.get("host") !== runtime.env.allowedHost) redirect("/");
  const sessionToken = (await cookies()).get(SESSION_COOKIE_NAME)?.value;
  try {
    const session = await runtime.auth.requireSession(sessionToken);
    return (
      <section>
        <h1>Protected session route</h1>
        <p data-testid="session-state">Authenticated by an authoritative database lookup.</p>
        <p>Session version: {session.sessionVersion}</p>
        <form action="/api/auth/logout" method="post">
          <button type="submit">Sign out locally</button>
        </form>
      </section>
    );
  } catch {
    redirect("/api/auth/login?returnTo=/protected");
  }
}
