import { describe, expect, it } from "vitest";

import {
  readAdminIfMatch,
  toAdminMutationResponse
} from "@/features/admin/admin-route-responses";

describe("tenant administration route responses", () => {
  it("accepts only strong numeric ETags for versioned mutations", () => {
    expect(readAdminIfMatch("\"3\"", true)).toBe("\"3\"");
    expect(() => readAdminIfMatch("W/\"3\"", true)).toThrow();
    expect(() => readAdminIfMatch(null, true)).toThrow();
    expect(() => readAdminIfMatch("\"3\"", false)).toThrow();
  });

  it("does not forward upstream administration representations", async () => {
    const response = toAdminMutationResponse(
      Response.json({ issuer: "https://private.example", subject: "secret" }),
      "correlation-1"
    );

    await expect(response.json()).resolves.toEqual({
      correlationId: "correlation-1",
      status: "accepted"
    });
    expect(response.headers.get("Cache-Control")).toBe("no-store");
  });

  it("maps conflicts without exposing upstream error bodies", async () => {
    const response = toAdminMutationResponse(
      Response.json({ stack: "internal-details" }, { status: 409 }),
      "correlation-2"
    );
    const body = await response.json() as Record<string, unknown>;

    expect(response.status).toBe(409);
    expect(body.code).toBe("admin_conflict");
    expect(JSON.stringify(body)).not.toContain("internal-details");
  });
});
