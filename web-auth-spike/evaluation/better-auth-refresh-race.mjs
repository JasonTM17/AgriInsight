import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import http from "node:http";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

const EXPECTED_VERSION = "1.6.24";
const moduleRoot = process.env.BETTER_AUTH_NODE_MODULES;
if (!moduleRoot) {
  throw new Error("BETTER_AUTH_NODE_MODULES is required");
}

const packageRoot = join(moduleRoot, "better-auth");
const manifest = JSON.parse(await readFile(join(packageRoot, "package.json"), "utf8"));
assert.equal(manifest.version, EXPECTED_VERSION);

const [{ betterAuth }, { memoryAdapter }, { genericOAuth }] = await Promise.all([
  import(pathToFileURL(join(packageRoot, "dist", "index.mjs")).href),
  import(pathToFileURL(join(
    packageRoot,
    "dist",
    "adapters",
    "memory-adapter",
    "index.mjs",
  )).href),
  import(pathToFileURL(join(
    packageRoot,
    "dist",
    "plugins",
    "generic-oauth",
    "index.mjs",
  )).href),
]);

let providerRefreshCalls = 0;
const provider = http.createServer(async (request, response) => {
  if (request.url !== "/token" || request.method !== "POST") {
    response.writeHead(404).end();
    return;
  }
  providerRefreshCalls += 1;
  const callNumber = providerRefreshCalls;
  await new Promise((resolve) => setTimeout(resolve, 150));
  response.writeHead(200, { "content-type": "application/json" });
  response.end(JSON.stringify({
    access_token: `access-${callNumber}`,
    expires_in: 3600,
    refresh_token: `refresh-${callNumber}`,
    token_type: "Bearer",
  }));
});

await new Promise((resolve) => provider.listen(0, "127.0.0.1", resolve));
const address = provider.address();
assert.equal(typeof address, "object");

try {
  const now = new Date();
  const database = {
    user: [{
      id: "user-1",
      name: "Test User",
      email: "test@example.invalid",
      emailVerified: true,
      createdAt: now,
      updatedAt: now,
    }],
    session: [],
    account: [{
      id: "account-1",
      accountId: "provider-user-1",
      providerId: "keycloak",
      userId: "user-1",
      accessToken: "expired-access",
      refreshToken: "refresh-0",
      accessTokenExpiresAt: new Date(now.getTime() - 1000),
      refreshTokenExpiresAt: new Date(now.getTime() + 3600000),
      scope: "openid profile email",
      createdAt: now,
      updatedAt: now,
    }],
    verification: [],
  };
  const auth = betterAuth({
    baseURL: "http://localhost:3100",
    database: memoryAdapter(database),
    secret: "agriinsight-better-auth-fit-secret-32-bytes",
    plugins: [genericOAuth({
      config: [{
        providerId: "keycloak",
        authorizationUrl: "http://localhost/authorize",
        tokenUrl: `http://127.0.0.1:${address.port}/token`,
        userInfoUrl: "http://localhost/userinfo",
        clientId: "client",
        clientSecret: "disposable-fit-test-value",
        pkce: true,
        scopes: ["openid", "profile", "email"],
      }],
    })],
  });

  const consumers = await Promise.all([
    auth.api.getAccessToken({ body: { providerId: "keycloak", userId: "user-1" } }),
    auth.api.getAccessToken({ body: { providerId: "keycloak", userId: "user-1" } }),
  ]);
  assert.equal(consumers.length, 2);
  assert.equal(providerRefreshCalls, 2);
  console.log(
    "BETTER_AUTH_REFRESH_FENCE=FAILED version=1.6.24 " +
    "provider_calls=2 concurrent_consumers=2",
  );
} finally {
  await new Promise((resolve, reject) => {
    provider.close((error) => error ? reject(error) : resolve());
  });
}
