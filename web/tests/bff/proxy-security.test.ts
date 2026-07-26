import { afterEach, describe, expect, it } from "vitest";
import { NextRequest } from "next/server";

import { proxy } from "@/proxy";

describe("web proxy security policy", () => {
  afterEach(() => {
    delete process.env.AGRIINSIGHT_WEB_ALLOWED_HOSTS;
    delete process.env.AGRIINSIGHT_WEB_BASE_URL;
    delete process.env.AGRIINSIGHT_WEB_TRUST_FORWARDED_HEADERS;
    delete process.env.AGRIINSIGHT_WEB_TRUSTED_PROXY_KEY_BASE64;
  });

  it("emits a per-request nonce CSP without unsafe script execution", () => {
    process.env.AGRIINSIGHT_WEB_ALLOWED_HOSTS = "localhost:3100";
    process.env.AGRIINSIGHT_WEB_BASE_URL = "http://localhost:3100";
    const response = proxy(
      new NextRequest("http://localhost:3100/", {
        headers: { Host: "localhost:3100" }
      })
    );
    const policy = response.headers.get("content-security-policy");
    expect(policy).toMatch(/script-src 'self' 'nonce-[a-f0-9]+' 'strict-dynamic'/);
    expect(policy).not.toContain("script-src 'self' 'unsafe-inline'");
    expect(policy).toMatch(/style-src 'self' 'nonce-[a-f0-9]+'/);
    expect(policy).not.toContain("style-src-attr");
    expect(policy).toContain("frame-ancestors 'none'");
    expect(policy).not.toContain("require-trusted-types-for");
  });

  it("rejects a foreign host before redirect or application work", () => {
    process.env.AGRIINSIGHT_WEB_ALLOWED_HOSTS = "localhost:3100";
    process.env.AGRIINSIGHT_WEB_BASE_URL = "http://localhost:3100";
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

  it("preserves a same-origin farm deep link through login", () => {
    process.env.AGRIINSIGHT_WEB_ALLOWED_HOSTS = "localhost:3100";
    process.env.AGRIINSIGHT_WEB_BASE_URL = "http://localhost:3100";
    const farmId = "3eb92f10-60dd-45cb-9160-7c569c3258b4";
    const response = proxy(
      new NextRequest(
        `http://localhost:3100/farms/${farmId}?status=all&sort=profit_desc`,
        { headers: { Host: "localhost:3100" } }
      )
    );
    expect(response.status).toBe(307);
    const location = new URL(response.headers.get("location")!);
    expect(location.pathname).toBe("/login");
    expect(location.searchParams.get("returnTo")).toBe(
      `/farms/${farmId}?status=all&sort=profit_desc`
    );
  });

  it("accepts matching framework forwarded metadata and rejects conflicts", () => {
    process.env.AGRIINSIGHT_WEB_ALLOWED_HOSTS = "localhost:3100";
    process.env.AGRIINSIGHT_WEB_BASE_URL = "http://localhost:3100";
    const matching = {
      Host: "localhost:3100",
      "X-Forwarded-For": "127.0.0.1",
      "X-Forwarded-Host": "localhost:3100",
      "X-Forwarded-Proto": "http"
    };
    expect(
      proxy(new NextRequest("http://localhost:3100/", { headers: matching }))
        .status
    ).toBe(200);
    expect(
      proxy(
        new NextRequest("http://localhost:3100/", {
          headers: { ...matching, "X-Forwarded-Host": "evil.example" }
        })
      ).status
    ).toBe(400);
  });

  it("requires proxy attestation for forwarded host metadata", () => {
    const proxyKey = Buffer.alloc(32, 8).toString("base64");
    process.env.AGRIINSIGHT_WEB_ALLOWED_HOSTS =
      "internal-proxy:3100,app.example";
    process.env.AGRIINSIGHT_WEB_BASE_URL = "https://app.example";
    process.env.AGRIINSIGHT_WEB_TRUST_FORWARDED_HEADERS = "true";
    process.env.AGRIINSIGHT_WEB_TRUSTED_PROXY_KEY_BASE64 = proxyKey;
    const headers = {
      Host: "internal-proxy:3100",
      "X-AgriInsight-Proxy-Attestation": proxyKey,
      "X-Forwarded-For": "203.0.113.5",
      "X-Forwarded-Host": "app.example",
      "X-Forwarded-Proto": "https"
    };
    expect(
      proxy(
        new NextRequest("http://internal-proxy:3100/", { headers })
      ).status
    ).toBe(200);
    expect(
      proxy(
        new NextRequest("http://internal-proxy:3100/", {
          headers: {
            ...headers,
            "X-AgriInsight-Proxy-Attestation": Buffer.alloc(32, 9).toString(
              "base64"
            )
          }
        })
      ).status
    ).toBe(400);
  });
});
