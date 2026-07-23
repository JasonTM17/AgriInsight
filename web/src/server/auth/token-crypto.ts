import "server-only";

import {
  createCipheriv,
  createDecipheriv,
  createHash,
  createHmac,
  randomBytes,
  timingSafeEqual
} from "node:crypto";

const ENVELOPE_VERSION = 1;
const IV_LENGTH = 12;
const TAG_LENGTH = 16;
const TOKEN_BYTES = 32;

export class TokenCipher {
  private readonly keys: ReadonlyMap<string, Buffer>;

  constructor(
    readonly keyId: string,
    key: Buffer,
    previousKeys: ReadonlyMap<string, Buffer> = new Map()
  ) {
    if (key.length !== 32) throw new Error("AES-256-GCM requires a 32-byte key");
    if (previousKeys.has(keyId)) {
      throw new Error("Current encryption key cannot also be a previous key");
    }
    this.keys = new Map([[keyId, key], ...previousKeys]);
  }

  seal(value: string, purpose: string): Buffer {
    return this.sealWithKey(this.keys.get(this.keyId)!, value, purpose, this.keyId);
  }

  open(envelope: Buffer, purpose: string): string {
    return this.openWithKeyId(this.keyId, envelope, purpose);
  }

  canOpen(keyId: string): boolean {
    return this.keys.has(keyId);
  }

  openWithKeyId(keyId: string, envelope: Buffer, purpose: string): string {
    const key = this.keys.get(keyId);
    if (!key) throw new Error("Unknown token encryption key");
    return this.openWithKey(key, keyId, envelope, purpose);
  }

  private sealWithKey(
    key: Buffer,
    value: string,
    purpose: string,
    keyId: string
  ): Buffer {
    const iv = randomBytes(IV_LENGTH);
    const cipher = createCipheriv("aes-256-gcm", key, iv);
    cipher.setAAD(Buffer.from(`${keyId}:${purpose}`, "utf8"));
    const ciphertext = Buffer.concat([cipher.update(value, "utf8"), cipher.final()]);
    return Buffer.concat([
      Buffer.from([ENVELOPE_VERSION]),
      iv,
      cipher.getAuthTag(),
      ciphertext
    ]);
  }

  private openWithKey(
    key: Buffer,
    keyId: string,
    envelope: Buffer,
    purpose: string
  ): string {
    if (envelope.length <= 1 + IV_LENGTH + TAG_LENGTH || envelope[0] !== 1) {
      throw new Error("Invalid token envelope");
    }
    const tagStart = 1 + IV_LENGTH;
    const ciphertextStart = tagStart + TAG_LENGTH;
    const decipher = createDecipheriv(
      "aes-256-gcm",
      key,
      envelope.subarray(1, tagStart)
    );
    decipher.setAAD(Buffer.from(`${keyId}:${purpose}`, "utf8"));
    decipher.setAuthTag(envelope.subarray(tagStart, ciphertextStart));
    return Buffer.concat([
      decipher.update(envelope.subarray(ciphertextStart)),
      decipher.final()
    ]).toString("utf8");
  }
}

export function randomOpaqueToken(): string {
  return randomBytes(TOKEN_BYTES).toString("base64url");
}

export function hashOpaqueToken(token: string): Buffer {
  return createHash("sha256").update(token, "utf8").digest();
}

export function signBoundValue(
  value: string,
  sessionToken: string,
  key: Buffer
): string {
  const signature = createHmac("sha256", key)
    .update(sessionToken)
    .update("\0")
    .update(value)
    .digest("base64url");
  return `${value}.${signature}`;
}

export function verifyBoundValue(
  signedValue: string,
  sessionToken: string,
  key: Buffer
): boolean {
  const separator = signedValue.lastIndexOf(".");
  if (separator <= 0) return false;
  const value = signedValue.slice(0, separator);
  const expected = signBoundValue(value, sessionToken, key);
  return (
    expected.length === signedValue.length &&
    timingSafeEqual(Buffer.from(expected), Buffer.from(signedValue))
  );
}
