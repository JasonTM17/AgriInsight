import { describe, expect, it } from "vitest";

import {
  assertTrustedRequest,
  loadWebEnvironment
} from "@/server/config/environment";

function source(overrides: Partial<NodeJS.ProcessEnv> = {}): NodeJS.ProcessEnv {
  return {
    NODE_ENV: "test",
    AGRIINSIGHT_WEB_BASE_URL: "https://app.agriinsight.example",
    AGRIINSIGHT_WEB_ALLOWED_HOSTS: "app.agriinsight.example",
    AGRIINSIGHT_WEB_TRUST_FORWARDED_HEADERS: "false",
    AGRIINSIGHT_WEB_OIDC_ISSUER: "https://issuer.example/realms/agri",
    AGRIINSIGHT_WEB_OIDC_CLIENT_ID: "web",
    AGRIINSIGHT_WEB_OIDC_CLIENT_SECRET: "client-secret",
    AGRIINSIGHT_WEB_SESSION_DATABASE_URL: "postgres://runtime@db/web",
    AGRIINSIGHT_WEB_SESSION_ENCRYPTION_KEY_BASE64:
      Buffer.alloc(32, 1).toString("base64"),
    AGRIINSIGHT_WEB_TOKEN_KEY_ID: "v1",
    AGRIINSIGHT_WEB_CSRF_KEY_BASE64: Buffer.alloc(32, 2).toString("base64"),
    AGRIINSIGHT_BACKEND_BASE_URL: "http://127.0.0.1:8080",
    AGRIINSIGHT_ANALYTICS_BASE_URL: "http://127.0.0.1:8081",
    ...overrides
  };
}

describe("effective host policy", () => {
  it("accepts only the configured origin when forwarded headers are disabled", () => {
    const env = loadWebEnvironment(source());
    const request = new Request("https://app.agriinsight.example/login", {
      headers: { Host: "app.agriinsight.example" }
    });
    expect(assertTrustedRequest(request, env).pathname).toBe("/login");
  });

  it("accepts framework-added forwarded metadata when it matches the origin", () => {
    const env = loadWebEnvironment(source());
    const request = new Request("https://app.agriinsight.example/login", {
      headers: {
        Host: "app.agriinsight.example",
        "X-Forwarded-Host": "app.agriinsight.example",
        "X-Forwarded-Proto": "https",
        "X-Forwarded-For": "127.0.0.1"
      }
    });
    expect(assertTrustedRequest(request, env).pathname).toBe("/login");
  });

  it("rejects conflicting forwarded metadata by default", () => {
    const env = loadWebEnvironment(source());
    const request = new Request("https://app.agriinsight.example/login", {
      headers: {
        Host: "app.agriinsight.example",
        "X-Forwarded-Host": "evil.example",
        "X-Forwarded-Proto": "http"
      }
    });
    expect(() => assertTrustedRequest(request, env)).toThrow(
      "Forwarded headers conflict with the request origin"
    );
  });

  it("rejects an unallowlisted direct host", () => {
    const env = loadWebEnvironment(source());
    const request = new Request("https://evil.example/login", {
      headers: { Host: "evil.example" }
    });
    expect(() => assertTrustedRequest(request, env)).toThrow();
  });

  it("requires proxy attestation before trusting forwarded host metadata", () => {
    const proxyKey = Buffer.alloc(32, 8).toString("base64");
    const env = loadWebEnvironment(
      source({
        AGRIINSIGHT_WEB_ALLOWED_HOSTS:
          "app.agriinsight.example,internal-proxy:3100",
        AGRIINSIGHT_WEB_TRUST_FORWARDED_HEADERS: "true",
        AGRIINSIGHT_WEB_TRUSTED_PROXY_KEY_BASE64: proxyKey
      })
    );
    const headers = {
      Host: "internal-proxy:3100",
      "X-Forwarded-Host": "app.agriinsight.example",
      "X-Forwarded-Proto": "https"
    };
    const unattested = new Request("http://internal-proxy:3100/login", {
      headers
    });
    expect(() => assertTrustedRequest(unattested, env)).toThrow(
      "Forwarded host is not allowed"
    );
    const attested = new Request("http://internal-proxy:3100/login", {
      headers: {
        ...headers,
        "X-AgriInsight-Proxy-Attestation": proxyKey
      }
    });
    expect(assertTrustedRequest(attested, env).pathname).toBe("/login");
  });
});
