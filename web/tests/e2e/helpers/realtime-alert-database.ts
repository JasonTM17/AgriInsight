import { Pool } from "pg";

import { requiredE2eEnvironment } from "./real-oidc-login";

// Stable `executive` persona values from deploy/demo/demo-tenant.json.
const EXECUTIVE_DEMO_TENANT_ID = "20000000-0000-4000-8000-000000000001";
const EXECUTIVE_DEMO_PROFILE_ID = "20000000-0000-4000-8000-000000000012";

const UUID_FORMAT =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const DEDUPE_KEY_FORMAT = /^[0-9a-f]{64}$/;

export type RealtimeAlertFixtureInput = Readonly<{
  alertId: string;
  dedupeKey: string;
}>;

export type RealtimeAlertFixture = Readonly<{
  alertId: string;
  dedupeKey: string;
  tenantId: string;
  profileId: string;
}>;

export function createRealtimeAlertDatabasePool(): Pool {
  return new Pool({
    connectionString: requiredE2eEnvironment(
      "AGRIINSIGHT_WEB_TEST_ADMIN_DATABASE_URL"
    ),
    max: 1
  });
}

export async function insertExecutiveOpenRealtimeAlert(
  pool: Pool,
  fixture: RealtimeAlertFixtureInput
): Promise<RealtimeAlertFixture> {
  assertFixtureInput(fixture);

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    const executiveProfile = await client.query<{ id: string }>(
      `SELECT profile.id::text AS id
       FROM tenants AS tenant
       JOIN user_profiles AS profile
         ON profile.tenant_id = tenant.id
       WHERE tenant.id = $1
         AND tenant.code = 'AGRIINSIGHT_DEMO'
         AND profile.id = $2`,
      [EXECUTIVE_DEMO_TENANT_ID, EXECUTIVE_DEMO_PROFILE_ID]
    );
    if (executiveProfile.rowCount !== 1) {
      throw new Error("Expected the stable executive demo profile");
    }

    const inserted = await client.query<{ id: string }>(
      `INSERT INTO realtime_operational_alerts (
         id,
         tenant_id,
         policy_code,
         dedupe_key,
         severity,
         state,
         source_event_id,
         source_occurred_at,
         opened_at,
         last_observed_at,
         resolved_at,
         clean_since,
         clean_scan_count,
         last_evaluated_at,
         version
       )
       VALUES (
         $1,
         $2,
         'OUTBOX_PUBLISH_BACKLOG',
         $3,
         'CRITICAL',
         'OPEN',
         NULL,
         CURRENT_TIMESTAMP - INTERVAL '2 minutes',
         CURRENT_TIMESTAMP - INTERVAL '1 minute',
         CURRENT_TIMESTAMP,
         NULL,
         NULL,
         0,
         CURRENT_TIMESTAMP,
         0
       )
       RETURNING id::text AS id`,
      [fixture.alertId, EXECUTIVE_DEMO_TENANT_ID, fixture.dedupeKey]
    );
    if (inserted.rowCount !== 1) {
      throw new Error("Expected one realtime alert fixture row");
    }

    await client.query("COMMIT");
    return {
      alertId: inserted.rows[0]!.id,
      dedupeKey: fixture.dedupeKey,
      tenantId: EXECUTIVE_DEMO_TENANT_ID,
      profileId: EXECUTIVE_DEMO_PROFILE_ID
    };
  } catch (error) {
    await client.query("ROLLBACK").catch(() => undefined);
    throw error;
  } finally {
    client.release();
  }
}

export async function deleteRealtimeAlertFixture(
  pool: Pool,
  fixture: RealtimeAlertFixture,
  options: Readonly<{ expectAcknowledgement?: boolean }> = {}
): Promise<void> {
  assertFixtureInput(fixture);
  assertFixtureScope(fixture);

  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const locked = await client.query<{ id: string }>(
      `SELECT alert.id
       FROM realtime_operational_alerts AS alert
       WHERE alert.tenant_id = $1
         AND alert.id = $2
         AND alert.dedupe_key = $3
       FOR UPDATE`,
      [fixture.tenantId, fixture.alertId, fixture.dedupeKey]
    );
    if (locked.rowCount !== 1) {
      throw new Error("Expected one locked realtime alert fixture row");
    }

    const deletedRevisions = await client.query(
      `DELETE FROM realtime_alert_acknowledgement_revisions AS revision
       WHERE revision.tenant_id = $1
         AND revision.alert_id = $2
         AND EXISTS (
           SELECT 1
           FROM realtime_operational_alerts AS alert
           WHERE alert.tenant_id = $1
             AND alert.id = $2
             AND alert.dedupe_key = $3
         )`,
      [fixture.tenantId, fixture.alertId, fixture.dedupeKey]
    );
    if (
      deletedRevisions.rowCount === null
      || deletedRevisions.rowCount > 1
      || (options.expectAcknowledgement && deletedRevisions.rowCount !== 1)
    ) {
      throw new Error("Unexpected realtime alert acknowledgement cleanup count");
    }

    const deletedAlert = await client.query(
      `DELETE FROM realtime_operational_alerts AS alert
       WHERE alert.tenant_id = $1
         AND alert.id = $2
         AND alert.dedupe_key = $3`,
      [fixture.tenantId, fixture.alertId, fixture.dedupeKey]
    );
    if (deletedAlert.rowCount !== 1) {
      throw new Error("Expected one deleted realtime alert fixture row");
    }
    await client.query("COMMIT");
  } catch (error) {
    await client.query("ROLLBACK").catch(() => undefined);
    throw error;
  } finally {
    client.release();
  }
}

function assertFixtureInput(fixture: RealtimeAlertFixtureInput): void {
  if (!UUID_FORMAT.test(fixture.alertId)) {
    throw new Error("Realtime alert fixture alertId must be a UUID");
  }
  if (!DEDUPE_KEY_FORMAT.test(fixture.dedupeKey)) {
    throw new Error("Realtime alert fixture dedupeKey must be 64 lowercase hex characters");
  }
}

function assertFixtureScope(fixture: RealtimeAlertFixture): void {
  if (
    fixture.tenantId !== EXECUTIVE_DEMO_TENANT_ID
    || fixture.profileId !== EXECUTIVE_DEMO_PROFILE_ID
  ) {
    throw new Error("Realtime alert fixture must target the stable executive demo scope");
  }
}
