import { afterEach, describe, expect, it } from "vitest";
import { NextRequest } from "next/server";

import { proxy } from "@/proxy";

describe("web proxy security policy", () => {
  afterEach(() => {
    delete process.env.AGRIINSIGHT_WEB_ALLOWED_HOSTS;
  });

  it("emits a per-request nonce CSP without unsafe script execution", () => {
    process.env.AGRIINSIGHT_WEB_ALLOWED_HOSTS = "localhost:3100";
    const response = proxy(
      new NextRequest("http://localhost:3100/", {
        headers: { Host: "localhost:3100" }
      })
    );
    const policy = response.headers.get("content-security-policy");
    expect(policy).toMatch(/script-src 'self' 'nonce-[a-f0-9]+' 'strict-dynamic'/);
    expect(policy).not.toContain("script-src 'self' 'unsafe-inline'");
    expect(policy).toContain("frame-ancestors 'none'");
    expect(policy).toContain("require-trusted-types-for 'script'");
  });

  it("rejects a foreign host before redirect or application work", () => {
    process.env.AGRIINSIGHT_WEB_ALLOWED_HOSTS = "localhost:3100";
    const response = proxy(
      new NextRequest("http://evil.example/protected", {
        headers: { Host: "evil.example" }
      })
    );
    expect(response.status).toBe(400);
    expect(response.headers.get("content-security-policy")).toContain(
      "default-src 'self'"
    );
  });
});
