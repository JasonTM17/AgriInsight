export const AUTH_ADAPTER_INVARIANTS = [
  "oidc-discovery-pkce-state-nonce",
  "encrypted-provider-tokens",
  "application-owned-database-schema",
  "opaque-browser-cookie",
  "local-session-revocation",
  "atomic-refresh-lease-version-fencing",
] as const;

export type AuthInvariant = (typeof AUTH_ADAPTER_INVARIANTS)[number];

export type AdapterFit = Readonly<{
  adapter: "better-auth" | "openid-client";
  version: string;
  satisfied: Readonly<Record<AuthInvariant, boolean>>;
  boundary: string;
}>;

export const BETTER_AUTH_FIT: AdapterFit = {
  adapter: "better-auth",
  version: "1.6.24",
  satisfied: {
    "oidc-discovery-pkce-state-nonce": true,
    "encrypted-provider-tokens": true,
    "application-owned-database-schema": true,
    "opaque-browser-cookie": true,
    "local-session-revocation": true,
    "atomic-refresh-lease-version-fencing": false,
  },
  boundary:
    "Generic OAuth owns its refresh/session lifecycle and exposes no transactional hook that can lease one provider refresh for an expected database session version. Adding that fence would replace the lifecycle being evaluated.",
};

export const OPENID_CLIENT_FIT: AdapterFit = {
  adapter: "openid-client",
  version: "6.8.4",
  satisfied: Object.fromEntries(
    AUTH_ADAPTER_INVARIANTS.map((invariant) => [invariant, true]),
  ) as Record<AuthInvariant, boolean>,
  boundary:
    "The library owns OIDC protocol validation only; the spike owns pre-auth records, encrypted tokens, opaque sessions, revocation, and PostgreSQL refresh fencing.",
};

export function failedInvariants(fit: AdapterFit): AuthInvariant[] {
  return AUTH_ADAPTER_INVARIANTS.filter((invariant) => !fit.satisfied[invariant]);
}

export function selectAdapter(candidates: readonly AdapterFit[]): AdapterFit {
  const winner = candidates.find((candidate) => failedInvariants(candidate).length === 0);
  if (!winner) {
    throw new Error("No auth adapter satisfies every must-pass invariant");
  }
  return winner;
}

export const SELECTED_ADAPTER = selectAdapter([BETTER_AUTH_FIT, OPENID_CLIENT_FIT]);
