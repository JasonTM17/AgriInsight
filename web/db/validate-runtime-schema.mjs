import pg from "pg";

const connectionString = process.env.AGRIINSIGHT_WEB_SESSION_DATABASE_URL;
if (!connectionString) {
  throw new Error("AGRIINSIGHT_WEB_SESSION_DATABASE_URL is required");
}

const pool = new pg.Pool({
  connectionString,
  max: 1,
  statement_timeout: 5_000,
  application_name: "agriinsight-web-schema-validator"
});

try {
  const result = await pool.query(
    "SELECT max(version)::integer AS version FROM agriinsight_web.schema_migrations"
  );
  if (result.rows[0]?.version !== 1) {
    throw new Error("Web session schema version 1 is required; run the one-shot migrator");
  }
  process.stdout.write("AgriInsight web runtime schema validation passed.\n");
} finally {
  await pool.end();
}
