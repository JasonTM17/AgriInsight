# Web-owned session database

The browser boundary owns only opaque authentication sessions in the
`agriinsight_web` schema. Spring business tables and Spring Flyway migrations
remain separate.

- `agriinsight_web_owner`: NOLOGIN table/schema owner.
- `agriinsight_web_migrator`: one-shot migration login; inherits the owner only
  for the migration process.
- `agriinsight_web_runtime`: DML on `preauth_requests` and `sessions`, read-only
  access to `schema_migrations`, and no DDL or grant-management privileges.

Passwords are passed to `psql` variables or process environment only. Never
write credentials into these SQL files or commit a populated dotenv file.

Run role bootstrap as a database administrator, then run:

```powershell
$env:AGRIINSIGHT_WEB_MIGRATOR_DATABASE_URL = "<process-only migrator URL>"
npm --prefix web run db:migrate

$env:AGRIINSIGHT_WEB_SESSION_DATABASE_URL = "<process-only runtime URL>"
npm --prefix web run db:validate
```

Runtime startup validates version `1`; it never applies DDL automatically.
