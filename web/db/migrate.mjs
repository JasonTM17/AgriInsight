import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

import pg from "pg";

const connectionString = process.env.AGRIINSIGHT_WEB_MIGRATOR_DATABASE_URL;
if (!connectionString) {
  throw new Error("AGRIINSIGHT_WEB_MIGRATOR_DATABASE_URL is required");
}

const migrationUrl = new URL(
  "./migrations/001-create-auth-session-schema.sql",
  import.meta.url
);
const sql = await readFile(fileURLToPath(migrationUrl), "utf8");
const pool = new pg.Pool({
  connectionString,
  max: 1,
  statement_timeout: 10_000,
  application_name: "agriinsight-web-migrator"
});

try {
  await pool.query(sql);
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await client.query("SET LOCAL ROLE agriinsight_web_owner");
    const result = await client.query(
      "SELECT version FROM agriinsight_web.schema_migrations ORDER BY version"
    );
    if (result.rows.at(-1)?.version !== 1) {
      throw new Error("Web schema migration version 1 was not recorded");
    }
    await client.query("COMMIT");
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
  process.stdout.write("AgriInsight web schema is at version 1.\n");
} finally {
  await pool.end();
}
