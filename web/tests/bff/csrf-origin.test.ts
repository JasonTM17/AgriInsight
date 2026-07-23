import { describe, expect, it } from "vitest";

import { AuthError } from "@/server/auth/auth-error";
import { assertCsrf, createCsrfToken } from "@/server/auth/csrf";
import { assertSameOriginMutation } from "@/server/auth/origin-guard";
import type { WebEnvironment } from "@/server/config/environment";

const key = Buffer.alloc(32, 9);
const env = {
  baseUrl: new URL("https://app.agriinsight.example")
} as WebEnvironment;

describe("mutation boundary", () => {
  it("requires same origin and a session-bound double-submit token", () => {
    const csrf = createCsrfToken("opaque-session", key);
    const request = new Request("https://app.agriinsight.example/api/action", {
      method: "POST",
      headers: {
        Origin: env.baseUrl.origin,
        "X-AgriInsight-Csrf": csrf
      }
    });
    assertSameOriginMutation(request, env);
    expect(() =>
      assertCsrf(request, csrf, "opaque-session", key)
    ).not.toThrow();
  });

  it.each([
    ["missing header", undefined, undefined, "opaque-session"],
    ["wrong cookie", "other", "signed", "opaque-session"],
    ["wrong session", undefined, "signed", "other-session"]
  ])("rejects %s before upstream work", (_label, cookieOverride, header, session) => {
    const csrf = createCsrfToken("opaque-session", key);
    const request = new Request("https://app.agriinsight.example/api/action", {
      method: "POST",
      headers: {
        Origin: env.baseUrl.origin,
        ...(header ? { "X-AgriInsight-Csrf": csrf } : {})
      }
    });
    expect(() =>
      assertCsrf(request, cookieOverride ?? csrf, session, key)
    ).toThrow(AuthError);
  });

  it("rejects a foreign origin", () => {
    const request = new Request("https://app.agriinsight.example/api/action", {
      method: "POST",
      headers: { Origin: "https://evil.example" }
    });
    expect(() => assertSameOriginMutation(request, env)).toThrow(AuthError);
  });
});
