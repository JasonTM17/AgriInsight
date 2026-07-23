import { randomUUID } from "node:crypto";

import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { Pool } from "pg";

const adminUrl = process.env.AGRIINSIGHT_WEB_TEST_ADMIN_DATABASE_URL;
const runtimeUrl = process.env.AGRIINSIGHT_WEB_SESSION_DATABASE_URL;
const enabled = Boolean(adminUrl && runtimeUrl);
const admin = enabled ? new Pool({ connectionString: adminUrl, max: 1 }) : null;
const runtime = enabled ? new Pool({ connectionString: runtimeUrl, max: 1 }) : null;

describe.runIf(enabled)("web session database privileges", () => {
  beforeAll(async () => {
    await admin?.query(
      "CREATE TABLE IF NOT EXISTS public.spring_business_probe (id integer PRIMARY KEY)"
    );
    await admin?.query("REVOKE ALL ON public.spring_business_probe FROM PUBLIC");
  });

  afterAll(async () => {
    await admin?.query("DROP TABLE IF EXISTS public.spring_business_probe");
    await runtime?.end();
    await admin?.end();
  });

  it("allows required session DML and schema-version reads", async () => {
    const version = await runtime?.query(
      "SELECT max(version)::integer AS version FROM agriinsight_web.schema_migrations"
    );
    expect(version?.rows[0]?.version).toBe(1);
    const id = randomUUID();
    const stateHash = Buffer.alloc(32, 1);
    const bindingHash = Buffer.alloc(32, 2);
    await runtime?.query(
      `INSERT INTO agriinsight_web.preauth_requests (
         id, state_hash, browser_binding_hash, pkce_verifier_ciphertext,
         nonce_ciphertext, token_key_id, return_path, expires_at
       ) VALUES ($1, $2, $3, $4, $5, 'test-v1', '/protected', now() + interval '1 minute')`,
      [id, stateHash, bindingHash, Buffer.from("pkce"), Buffer.from("nonce")]
    );
    const consumed = await runtime?.query(
      `UPDATE agriinsight_web.preauth_requests
       SET consumed_at = now()
       WHERE id = $1
       RETURNING id`,
      [id]
    );
    expect(consumed?.rowCount).toBe(1);
    await runtime?.query(
      "DELETE FROM agriinsight_web.preauth_requests WHERE id = $1",
      [id]
    );
  });

  it.each([
    "CREATE TABLE agriinsight_web.runtime_ddl_probe (id integer)",
    "ALTER TABLE agriinsight_web.sessions ADD COLUMN runtime_probe text",
    "DROP TABLE agriinsight_web.sessions",
    "SELECT * FROM public.spring_business_probe"
  ])("denies runtime escalation: %s", async (sql) => {
    await expect(runtime?.query(sql)).rejects.toBeTruthy();
  });

  it("cannot widen grants even when PostgreSQL reports a warning-only GRANT", async () => {
    await runtime?.query(
      "GRANT SELECT ON agriinsight_web.sessions TO PUBLIC"
    );
    const result = await admin?.query(
      `SELECT has_table_privilege(
         'public',
         'agriinsight_web.sessions',
         'SELECT'
       ) AS granted`
    );
    expect(result?.rows[0]?.granted).toBe(false);
  });

  it("keeps the runtime role non-privileged", async () => {
    const result = await runtime?.query(
      `SELECT rolsuper, rolcreatedb, rolcreaterole, rolinherit, rolbypassrls
       FROM pg_roles WHERE rolname = current_user`
    );
    expect(result?.rows[0]).toEqual({
      rolbypassrls: false,
      rolcreatedb: false,
      rolcreaterole: false,
      rolinherit: false,
      rolsuper: false
    });
  });
});
