import { Pool } from "pg";

import { requiredE2eEnvironment } from "./real-oidc-login";

export type ActivityTarget = Readonly<{
  activityId: string;
  activityVersion: string;
  employeeId: string;
}>;

export function createAdminDatabasePool(): Pool {
  return new Pool({
    connectionString: requiredE2eEnvironment(
      "AGRIINSIGHT_WEB_TEST_ADMIN_DATABASE_URL"
    ),
    max: 1
  });
}

export async function findUnassignedFieldWorkerActivity(
  pool: Pool
): Promise<ActivityTarget> {
  const result = await pool.query<{
    activity_id: string;
    activity_version: string;
    employee_id: string;
  }>(
    `SELECT activity.id::text AS activity_id,
            activity.version::text AS activity_version,
            employee.id::text AS employee_id
     FROM activities AS activity
     JOIN employees AS employee
       ON employee.tenant_id = activity.tenant_id
      AND employee.code = 'DEMO-FIELD-WORKER'
     WHERE NOT EXISTS (
       SELECT 1 FROM activity_assignees AS assignment
       WHERE assignment.tenant_id = activity.tenant_id
         AND assignment.activity_id = activity.id
         AND assignment.employee_id = employee.id
         AND assignment.revoked_at IS NULL
     )
     ORDER BY activity.id
     LIMIT 1`
  );
  if (result.rowCount !== 1) {
    throw new Error("Expected one unassigned field-worker activity");
  }
  return {
    activityId: result.rows[0]!.activity_id,
    activityVersion: result.rows[0]!.activity_version,
    employeeId: result.rows[0]!.employee_id
  };
}

export async function findActiveActivityAssignment(
  pool: Pool,
  target: ActivityTarget
): Promise<Readonly<{ id: string; version: string }>> {
  const result = await pool.query<{ id: string; version: string }>(
    `SELECT id::text, version::text
     FROM activity_assignees
     WHERE activity_id = $1 AND employee_id = $2 AND revoked_at IS NULL`,
    [target.activityId, target.employeeId]
  );
  if (result.rowCount !== 1) {
    throw new Error("Expected one active activity assignment");
  }
  return result.rows[0]!;
}

export async function revokeActivityAssignmentForCleanup(
  pool: Pool,
  assignmentId: string
): Promise<void> {
  await pool.query(
    `UPDATE activity_assignees
     SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP),
         version = CASE WHEN revoked_at IS NULL THEN version + 1 ELSE version END,
         updated_at = CURRENT_TIMESTAMP
     WHERE id = $1`,
    [assignmentId]
  );
}
