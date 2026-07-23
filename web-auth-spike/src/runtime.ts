import { Pool } from "pg";

import { AuthService } from "./auth-service.ts";
import { loadAuthEnvironment } from "./env.ts";
import { OpenIdClientProvider } from "./oidc-flow.ts";
import { PostgresSessionStore } from "./postgres-session-store.ts";
import { TokenCipher } from "./token-crypto.ts";

type AuthRuntime = Readonly<{
  auth: AuthService;
  env: ReturnType<typeof loadAuthEnvironment>;
  provider: OpenIdClientProvider;
  store: PostgresSessionStore;
}>;

const globalRuntime = globalThis as typeof globalThis & {
  __agriInsightAuthSpikeRuntime?: AuthRuntime;
};

export function getAuthRuntime(): AuthRuntime {
  if (globalRuntime.__agriInsightAuthSpikeRuntime) {
    return globalRuntime.__agriInsightAuthSpikeRuntime;
  }
  const env = loadAuthEnvironment();
  const pool = new Pool({
    connectionString: env.databaseUrl,
    max: 10,
    statement_timeout: 5000,
  });
  const store = new PostgresSessionStore(pool);
  const provider = new OpenIdClientProvider(env);
  const runtime = {
    auth: new AuthService(
      env,
      store,
      new TokenCipher(env.keyId, env.encryptionKey),
      provider,
    ),
    env,
    provider,
    store,
  };
  globalRuntime.__agriInsightAuthSpikeRuntime = runtime;
  return runtime;
}
