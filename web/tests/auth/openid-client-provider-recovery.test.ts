import { beforeEach, describe, expect, it, vi } from "vitest";

const openidMocks = vi.hoisted(() => ({
  buildAuthorizationUrl: vi.fn(),
  discovery: vi.fn()
}));

vi.mock("openid-client", () => ({
  ClientSecretBasic: vi.fn(() => ({})),
  ResponseBodyError: class extends Error {},
  allowInsecureRequests: Symbol("allow-insecure"),
  authorizationCodeGrant: vi.fn(),
  buildAuthorizationUrl: openidMocks.buildAuthorizationUrl,
  buildEndSessionUrl: vi.fn(),
  calculatePKCECodeChallenge: vi.fn(),
  discovery: openidMocks.discovery,
  randomPKCECodeVerifier: vi.fn(),
  refreshTokenGrant: vi.fn(),
  tokenRevocation: vi.fn()
}));

import { OpenIdClientProvider } from "@/server/auth/openid-client-provider";
import type { WebEnvironment } from "@/server/config/environment";

const env = {
  callbackUrl: new URL("http://localhost:3100/api/auth/callback"),
  clientId: "agriinsight-web",
  clientSecret: "secret",
  issuer: new URL("http://localhost:58080/realms/agriinsight-demo")
} as WebEnvironment;

describe("OpenID client provider discovery recovery", () => {
  beforeEach(() => {
    openidMocks.buildAuthorizationUrl.mockReset();
    openidMocks.discovery.mockReset();
  });

  it("does not poison the discovery cache after a transient failure", async () => {
    const configuration = {
      serverMetadata: () => ({
        issuer: env.issuer.href.replace(/\/$/, ""),
        supportsPKCE: () => true
      }),
      timeout: 0
    };
    openidMocks.discovery
      .mockRejectedValueOnce(new Error("temporary issuer outage"))
      .mockResolvedValueOnce(configuration);
    openidMocks.buildAuthorizationUrl.mockReturnValue(
      new URL("http://localhost:58080/authorize")
    );
    const provider = new OpenIdClientProvider(env);
    const input = {
      codeChallenge: "challenge",
      nonce: "nonce",
      state: "state"
    };

    await expect(provider.buildAuthorizationRedirect(input)).rejects.toThrow(
      "temporary issuer outage"
    );
    await expect(provider.buildAuthorizationRedirect(input)).resolves.toEqual(
      new URL("http://localhost:58080/authorize")
    );
    expect(openidMocks.discovery).toHaveBeenCalledTimes(2);
  });
});
