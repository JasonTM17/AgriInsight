import "server-only";

import { Pool } from "pg";

import { AuthService } from "@/server/auth/auth-service";
import { OpenIdClientProvider } from "@/server/auth/openid-client-provider";
import { PostgresSessionStore } from "@/server/auth/postgres-session-store";
import { TokenCipher } from "@/server/auth/token-crypto";
import {
  loadWebEnvironment,
  type WebEnvironment
} from "@/server/config/environment";

type AuthRuntime = Readonly<{
  auth: AuthService;
  env: WebEnvironment;
  provider: OpenIdClientProvider;
  store: PostgresSessionStore;
}>;

const globalRuntime = globalThis as typeof globalThis & {
  __agriInsightWebAuthRuntime?: AuthRuntime;
};

export function getAuthRuntime(): AuthRuntime {
  if (globalRuntime.__agriInsightWebAuthRuntime) {
    return globalRuntime.__agriInsightWebAuthRuntime;
  }
  const env = loadWebEnvironment();
  const pool = new Pool({
    connectionString: env.databaseUrl,
    max: 10,
    statement_timeout: 5_000,
    application_name: "agriinsight-web-runtime"
  });
  const store = new PostgresSessionStore(pool);
  const provider = new OpenIdClientProvider(env);
  const runtime = {
    auth: new AuthService(
      env,
      store,
      new TokenCipher(env.keyId, env.encryptionKey),
      provider
    ),
    env,
    provider,
    store
  };
  globalRuntime.__agriInsightWebAuthRuntime = runtime;
  return runtime;
}
