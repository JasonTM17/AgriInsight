import { describe, expect, it } from "vitest";

import { authErrorResponse } from "@/server/auth/auth-http";

describe("browser-visible error boundary", () => {
  it("does not expose provider tokens or internal causes", async () => {
    const error = new Error(
      "upstream rejected access-token-secret and refresh-token-secret"
    );
    const response = authErrorResponse(error);
    const payload = await response.text();
    expect(payload).not.toContain("access-token-secret");
    expect(payload).not.toContain("refresh-token-secret");
    expect(payload).not.toContain("upstream rejected");
    expect(response.headers.get("cache-control")).toBe("no-store");
  });
});
