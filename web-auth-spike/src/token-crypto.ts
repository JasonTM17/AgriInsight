import {
  createCipheriv,
  createDecipheriv,
  createHash,
  randomBytes,
} from "node:crypto";

const ENVELOPE_VERSION = 1;
const IV_LENGTH = 12;
const TAG_LENGTH = 16;
const TOKEN_BYTES = 32;

export class TokenCipher {
  constructor(
    readonly keyId: string,
    private readonly key: Buffer,
  ) {
    if (key.length !== 32) throw new Error("AES-256-GCM requires a 32-byte key");
  }

  seal(value: string, purpose: string): Buffer {
    const iv = randomBytes(IV_LENGTH);
    const cipher = createCipheriv("aes-256-gcm", this.key, iv);
    cipher.setAAD(Buffer.from(`${this.keyId}:${purpose}`, "utf8"));
    const ciphertext = Buffer.concat([cipher.update(value, "utf8"), cipher.final()]);
    return Buffer.concat([
      Buffer.from([ENVELOPE_VERSION]),
      iv,
      cipher.getAuthTag(),
      ciphertext,
    ]);
  }

  open(envelope: Buffer, purpose: string): string {
    if (envelope.length <= 1 + IV_LENGTH + TAG_LENGTH || envelope[0] !== ENVELOPE_VERSION) {
      throw new Error("Invalid token envelope");
    }
    const ivStart = 1;
    const tagStart = ivStart + IV_LENGTH;
    const ciphertextStart = tagStart + TAG_LENGTH;
    const decipher = createDecipheriv(
      "aes-256-gcm",
      this.key,
      envelope.subarray(ivStart, tagStart),
    );
    decipher.setAAD(Buffer.from(`${this.keyId}:${purpose}`, "utf8"));
    decipher.setAuthTag(envelope.subarray(tagStart, ciphertextStart));
    return Buffer.concat([
      decipher.update(envelope.subarray(ciphertextStart)),
      decipher.final(),
    ]).toString("utf8");
  }
}

export function randomOpaqueToken(): string {
  return randomBytes(TOKEN_BYTES).toString("base64url");
}

export function hashOpaqueToken(token: string): Buffer {
  return createHash("sha256").update(token, "utf8").digest();
}
