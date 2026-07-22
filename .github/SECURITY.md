# Security Policy

## Supported branch

The `main` branch is the supported development line. Releases will be listed
here once the Phase 7 release gate is complete.

## Reporting a vulnerability

Please report suspected vulnerabilities privately through GitHub Security
Advisories when private reporting is available for this repository. If it is
not available, contact the repository owner privately through GitHub and include
the affected component, reproduction steps, impact, and a safe mitigation.

Do not open a public issue, attach credentials, or paste tokens/private keys in
any report. The project will acknowledge a valid report, coordinate a fix, and
publish a release note after the issue is resolved.

## Security boundaries

- Never commit `.env`, API keys, OIDC secrets, database passwords, Docker Hub
  tokens, private keys, or personal data.
- Backend runtime uses provider-neutral OIDC, tenant/profile transaction
  context, PostgreSQL FORCE RLS, role-aware warehouse scope, and idempotent
  commands.
- API documentation is disabled by default and must not be enabled in a
  production profile without authentication and an explicit review.
