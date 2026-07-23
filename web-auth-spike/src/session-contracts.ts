export type EncryptedValue = Readonly<{
  ciphertext: Buffer;
  keyId: string;
}>;

export type CreatePreauthInput = Readonly<{
  browserBindingHash: Buffer;
  expiresAt: Date;
  nonce: EncryptedValue;
  pkceVerifier: EncryptedValue;
  returnPath: string;
  stateHash: Buffer;
}>;

export type ConsumedPreauth = Readonly<{
  nonceCiphertext: Buffer;
  pkceVerifierCiphertext: Buffer;
  returnPath: string;
  tokenKeyId: string;
}>;

export type ProviderTokens = Readonly<{
  accessToken: string;
  accessTokenExpiresAt: Date;
  idToken?: string;
  refreshToken?: string;
  subject: string;
}>;

export type RefreshedProviderTokens = Readonly<{
  accessToken: string;
  accessTokenExpiresAt: Date;
  idToken?: string;
  refreshToken?: string;
}>;

export type CreateSessionInput = Readonly<{
  accessToken: EncryptedValue;
  accessTokenExpiresAt: Date;
  idToken?: EncryptedValue;
  issuer: string;
  refreshToken?: EncryptedValue;
  sessionExpiresAt: Date;
  sessionTokenHash: Buffer;
  subject: string;
}>;

export type StoredSession = Readonly<{
  accessTokenCiphertext: Buffer;
  accessTokenExpiresAt: Date;
  id: string;
  idTokenCiphertext: Buffer | null;
  issuer: string;
  refreshLeaseExpiresAt: Date | null;
  refreshLeaseId: string | null;
  refreshTokenCiphertext: Buffer | null;
  revokedAt: Date | null;
  sessionExpiresAt: Date;
  sessionVersion: number;
  subject: string;
  tokenKeyId: string;
}>;

export type RefreshLease = Readonly<{
  leaseId: string;
  refreshTokenCiphertext: Buffer;
  sessionId: string;
  sessionVersion: number;
  subject: string;
  tokenKeyId: string;
}>;

export type RotateSessionInput = Readonly<{
  accessToken: EncryptedValue;
  accessTokenExpiresAt: Date;
  expectedSessionVersion: number;
  idToken?: EncryptedValue;
  leaseId: string;
  refreshToken?: EncryptedValue;
  sessionId: string;
}>;

export interface SessionStore {
  createPreauth(input: CreatePreauthInput): Promise<void>;
  consumePreauth(stateHash: Buffer, bindingHash: Buffer, now: Date): Promise<ConsumedPreauth | null>;
  createSession(input: CreateSessionInput): Promise<void>;
  findSession(sessionTokenHash: Buffer): Promise<StoredSession | null>;
  acquireRefreshLease(sessionTokenHash: Buffer, now: Date): Promise<RefreshLease | null>;
  rotateSession(input: RotateSessionInput): Promise<boolean>;
  finishTransientRefreshFailure(lease: RefreshLease, retryAfter: Date): Promise<void>;
  revokeRefreshLease(lease: RefreshLease, now: Date): Promise<void>;
  revokeSession(sessionTokenHash: Buffer, now: Date): Promise<StoredSession | null>;
}
