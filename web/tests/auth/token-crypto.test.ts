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

  it("opens prior-key ciphertext during a bounded key rotation window", () => {
    const oldKey = Buffer.alloc(32, 4);
    const oldCipher = new TokenCipher("old-v1", oldKey);
    const sealed = oldCipher.seal("rotating-token", "session:access");
    const ring = new TokenCipher(
      "current-v2",
      Buffer.alloc(32, 5),
      new Map([["old-v1", oldKey]])
    );
    expect(
      ring.openWithKeyId("old-v1", sealed, "session:access")
    ).toBe("rotating-token");
    expect(ring.canOpen("retired-v0")).toBe(false);
    expect(() =>
      ring.openWithKeyId("retired-v0", sealed, "session:access")
    ).toThrow("Unknown token encryption key");
  });
});
