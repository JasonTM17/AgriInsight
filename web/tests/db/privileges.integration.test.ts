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

  it("denies public schema/database creation and every role membership", async () => {
    const privileges = await runtime?.query(
      `SELECT
         has_database_privilege(
           current_user,
           current_database(),
           'CREATE'
         ) AS can_create_database_objects,
         has_schema_privilege(
           current_user,
           'public',
           'CREATE'
         ) AS can_create_in_public`
    );
    expect(privileges?.rows[0]).toEqual({
      can_create_database_objects: false,
      can_create_in_public: false
    });
    const memberships = await runtime?.query(
      `SELECT count(*)::integer AS membership_count
       FROM pg_catalog.pg_auth_members AS membership
       JOIN pg_catalog.pg_roles AS member_role
         ON member_role.oid = membership.member
       WHERE member_role.rolname = current_user`
    );
    expect(memberships?.rows[0]?.membership_count).toBe(0);
    await expect(
      runtime?.query("SET ROLE agriinsight_web_owner")
    ).rejects.toBeTruthy();
  });

  it("keeps owner/migrator attributes and membership options exact", async () => {
    const roles = await admin?.query(
      `SELECT rolname, rolcanlogin, rolsuper, rolinherit, rolcreatedb,
              rolcreaterole, rolreplication, rolbypassrls
       FROM pg_catalog.pg_roles
       WHERE rolname IN (
         'agriinsight_web_owner',
         'agriinsight_web_migrator',
         'agriinsight_web_runtime'
       )
       ORDER BY rolname`
    );
    expect(roles?.rows).toEqual([
      {
        rolbypassrls: false,
        rolcanlogin: true,
        rolcreatedb: false,
        rolcreaterole: false,
        rolinherit: false,
        rolname: "agriinsight_web_migrator",
        rolreplication: false,
        rolsuper: false
      },
      {
        rolbypassrls: false,
        rolcanlogin: false,
        rolcreatedb: false,
        rolcreaterole: false,
        rolinherit: false,
        rolname: "agriinsight_web_owner",
        rolreplication: false,
        rolsuper: false
      },
      {
        rolbypassrls: false,
        rolcanlogin: true,
        rolcreatedb: false,
        rolcreaterole: false,
        rolinherit: false,
        rolname: "agriinsight_web_runtime",
        rolreplication: false,
        rolsuper: false
      }
    ]);
    const membership = await admin?.query(
      `SELECT granted_role.rolname AS granted_role,
              member_role.rolname AS member_role,
              membership.admin_option,
              membership.inherit_option,
              membership.set_option
       FROM pg_catalog.pg_auth_members AS membership
       JOIN pg_catalog.pg_roles AS granted_role
         ON granted_role.oid = membership.roleid
       JOIN pg_catalog.pg_roles AS member_role
         ON member_role.oid = membership.member
       WHERE granted_role.rolname LIKE 'agriinsight_web_%'
          OR member_role.rolname LIKE 'agriinsight_web_%'`
    );
    expect(membership?.rows).toEqual([
      {
        admin_option: false,
        granted_role: "agriinsight_web_owner",
        inherit_option: false,
        member_role: "agriinsight_web_migrator",
        set_option: true
      }
    ]);
  });
});
