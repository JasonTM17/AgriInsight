# Contributing to AgriInsight

Thank you for helping improve AgriInsight. Keep changes small, reviewable, and
backed by evidence. The project is a two-plane system: Python owns the current
Bronze/Silver/Gold analytics contracts; the Java/PostgreSQL backend owns
tenant-scoped operational data. Do not cross those ownership boundaries without
an approved plan and versioned contract.

## Before opening a pull request

1. Read `README.md`, `AGENTS.md`, and the relevant files under `docs/` and
   `plans/`.
2. Run the disk guard before and after resource-heavy commands:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/check-workspace-disk.ps1
   ```

3. Keep Maven repository/temp output on D:

   ```powershell
   $env:TEMP='D:\AgriInsight\tmp\java'
   $env:TMP=$env:TEMP
   $env:MAVEN_OPTS='-Xmx384m -Djava.io.tmpdir=D:\AgriInsight\tmp\java'
   ```

4. Run the narrowest useful test first, then the full gate when shared
   contracts, migrations, security, or public APIs changed.

## Validation commands

```powershell
python -m pytest
python -m compileall -q src dashboard tests
powershell -ExecutionPolicy Bypass -File scripts/run-backend-tests.ps1 verify
git diff --check
```

Use the repository Maven wrapper and the D-local repository configured by the
backend workflow. Never commit `.env`, tokens, private keys, database
credentials, generated reports, or Docker credentials.

## Change and commit standards

- Prefer a focused change with one responsibility.
- Preserve tenant context, profile context, RLS, idempotency, and public error
  contracts when changing backend code.
- Add tests for happy paths, boundary conditions, authorization, and rollback
  behavior at the layer where the invariant is enforced.
- Use Conventional Commits (`feat:`, `fix:`, `test:`, `refactor:`, `build:`).
- Keep commit messages free of AI references and secrets.
- Update docs when behavior, setup, architecture, security, or public contracts
  change.

## Pull requests

Describe the problem, the scope, validation commands and results, migration or
rollback impact, security impact, and any unresolved decisions. Keep unrelated
refactors out of feature PRs. A maintainer must review database migrations,
authorization/RLS changes, and release workflows.
