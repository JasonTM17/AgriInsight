import { describe, expect, it } from "vitest";

import {
  TokenCipher,
  signBoundValue,
  verifyBoundValue
} from "@/server/auth/token-crypto";

const key = Buffer.alloc(32, 7);

describe("server token cryptography", () => {
  it("encrypts provider tokens with purpose-bound authenticated encryption", () => {
    const cipher = new TokenCipher("test-v1", key);
    const sealed = cipher.seal("access-secret", "session:access");
    expect(sealed.toString("utf8")).not.toContain("access-secret");
    expect(cipher.open(sealed, "session:access")).toBe("access-secret");
    expect(() => cipher.open(sealed, "session:refresh")).toThrow();
  });

  it("rejects tampered encrypted envelopes", () => {
    const cipher = new TokenCipher("test-v1", key);
    const sealed = cipher.seal("access-secret", "session:access");
    sealed[sealed.length - 1] ^= 1;
    expect(() => cipher.open(sealed, "session:access")).toThrow();
  });

  it("binds signed browser values to the opaque session", () => {
    const signed = signBoundValue("csrf-value", "session-a", key);
    expect(verifyBoundValue(signed, "session-a", key)).toBe(true);
    expect(verifyBoundValue(signed, "session-b", key)).toBe(false);
  });
});
