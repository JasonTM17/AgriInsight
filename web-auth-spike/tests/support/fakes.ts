import { randomUUID } from "node:crypto";

import type {
  ConsumedPreauth,
  CreatePreauthInput,
  CreateSessionInput,
  RefreshLease,
  RotateSessionInput,
  SessionStore,
  StoredSession,
} from "../../src/session-contracts.ts";

export { FakeProvider } from "./fake-provider.ts";

const hex = (value: Buffer) => value.toString("hex");

export class InMemorySessionStore implements SessionStore {
  private readonly preauth = new Map<string, CreatePreauthInput & { consumed: boolean }>();
  readonly sessions = new Map<string, StoredSession>();

  async createPreauth(input: CreatePreauthInput): Promise<void> {
    this.preauth.set(hex(input.stateHash), { ...input, consumed: false });
  }

  async consumePreauth(
    stateHash: Buffer,
    bindingHash: Buffer,
    now: Date,
  ): Promise<ConsumedPreauth | null> {
    const row = this.preauth.get(hex(stateHash));
    if (
      !row ||
      row.consumed ||
      !row.browserBindingHash.equals(bindingHash) ||
      row.expiresAt <= now
    ) return null;
    row.consumed = true;
    return {
      nonceCiphertext: row.nonce.ciphertext,
      pkceVerifierCiphertext: row.pkceVerifier.ciphertext,
      returnPath: row.returnPath,
      tokenKeyId: row.nonce.keyId,
    };
  }

  async createSession(input: CreateSessionInput): Promise<void> {
    this.sessions.set(hex(input.sessionTokenHash), {
      accessTokenCiphertext: input.accessToken.ciphertext,
      accessTokenExpiresAt: input.accessTokenExpiresAt,
      id: randomUUID(),
      idTokenCiphertext: input.idToken?.ciphertext ?? null,
      issuer: input.issuer,
      refreshLeaseExpiresAt: null,
      refreshLeaseId: null,
      refreshTokenCiphertext: input.refreshToken?.ciphertext ?? null,
      revokedAt: null,
      sessionExpiresAt: input.sessionExpiresAt,
      sessionVersion: 1,
      subject: input.subject,
      tokenKeyId: input.accessToken.keyId,
    });
  }

  async findSession(sessionTokenHash: Buffer): Promise<StoredSession | null> {
    return this.sessions.get(hex(sessionTokenHash)) ?? null;
  }

  async acquireRefreshLease(
    sessionTokenHash: Buffer,
    now: Date,
  ): Promise<RefreshLease | null> {
    const key = hex(sessionTokenHash);
    const row = this.sessions.get(key);
    if (
      !row ||
      row.revokedAt ||
      !row.refreshTokenCiphertext ||
      row.sessionExpiresAt <= now ||
      row.accessTokenExpiresAt.getTime() > now.getTime() + 30_000 ||
      (row.refreshLeaseId && row.refreshLeaseExpiresAt && row.refreshLeaseExpiresAt > now)
    ) return null;
    const leaseId = randomUUID();
    this.sessions.set(key, {
      ...row,
      refreshLeaseExpiresAt: new Date(now.getTime() + 15_000),
      refreshLeaseId: leaseId,
    });
    return {
      leaseId,
      refreshTokenCiphertext: row.refreshTokenCiphertext,
      sessionId: row.id,
      sessionVersion: row.sessionVersion,
      subject: row.subject,
      tokenKeyId: row.tokenKeyId,
    };
  }

  async rotateSession(input: RotateSessionInput): Promise<boolean> {
    const entry = [...this.sessions.entries()].find(([, row]) => row.id === input.sessionId);
    if (!entry) return false;
    const [key, row] = entry;
    if (
      row.revokedAt ||
      row.sessionVersion !== input.expectedSessionVersion ||
      row.refreshLeaseId !== input.leaseId
    ) return false;
    this.sessions.set(key, {
      ...row,
      accessTokenCiphertext: input.accessToken.ciphertext,
      accessTokenExpiresAt: input.accessTokenExpiresAt,
      idTokenCiphertext: input.idToken?.ciphertext ?? row.idTokenCiphertext,
      refreshLeaseExpiresAt: null,
      refreshLeaseId: null,
      refreshTokenCiphertext: input.refreshToken?.ciphertext ?? row.refreshTokenCiphertext,
      sessionVersion: row.sessionVersion + 1,
      tokenKeyId: input.accessToken.keyId,
    });
    return true;
  }

  async finishTransientRefreshFailure(lease: RefreshLease): Promise<void> {
    this.updateLease(lease, false);
  }

  async revokeRefreshLease(lease: RefreshLease, now: Date): Promise<void> {
    this.updateLease(lease, true, now);
  }

  async revokeSession(
    sessionTokenHash: Buffer,
    now: Date,
  ): Promise<StoredSession | null> {
    const key = hex(sessionTokenHash);
    const row = this.sessions.get(key);
    if (!row) return null;
    const revoked = {
      ...row,
      refreshLeaseExpiresAt: null,
      refreshLeaseId: null,
      revokedAt: row.revokedAt ?? now,
      sessionVersion: row.revokedAt ? row.sessionVersion : row.sessionVersion + 1,
    };
    this.sessions.set(key, revoked);
    return revoked;
  }

  private updateLease(lease: RefreshLease, revoke: boolean, now = new Date()): void {
    const entry = [...this.sessions.entries()].find(([, row]) => row.id === lease.sessionId);
    if (!entry) return;
    const [key, row] = entry;
    if (row.refreshLeaseId !== lease.leaseId || row.sessionVersion !== lease.sessionVersion) return;
    this.sessions.set(key, {
      ...row,
      refreshLeaseExpiresAt: null,
      refreshLeaseId: null,
      revokedAt: revoke ? now : row.revokedAt,
      sessionVersion: row.sessionVersion + 1,
    });
  }
}
