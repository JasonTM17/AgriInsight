# Research: Spring stack and identity boundary

Date: 2026-07-19

## Evidence

- The repository is Python-first and explicitly lists Java/Spring authentication and row-level authorization as the next extension. There is no Java or identity implementation to preserve.
- The supplied specification names Java 21, Spring Boot, Spring Security, Spring Data JPA, REST, PostgreSQL, Flyway, and OpenAPI. It lists Kafka/Redis/microservices as future-scale tools, not a requirement to deploy them immediately.
- Spring Boot's current system-requirements page lists 4.1.0 as stable, requiring Java 17+ and supporting Java through 26; Java 21 is therefore a valid project baseline: <https://docs.spring.io/spring-boot/system-requirements.html>.
- Spring Modulith describes functional application modules and verification of cycles/internal access: <https://docs.spring.io/spring-modulith/reference/index.html> and <https://docs.spring.io/spring-modulith/reference/verification.html>.
- Spring Security Resource Server validates JWT signature, issuer, timestamps, and scopes through the configured issuer/JWKS: <https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html>.
- Spring Authorization Server implements OAuth 2.1/OIDC but makes the application own an identity provider, client registry, key lifecycle, and token lifecycle: <https://docs.spring.io/spring-authorization-server/reference/overview.html>.
- Spring Security password guidance recommends adaptive one-way encoders and short-lived credentials, but this plan intentionally stores no human passwords: <https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html>.
- OWASP ASVS 5.0 is used as the security verification checklist, not as a claim that framework defaults are sufficient: <https://owasp.org/www-project-application-security-verification-standard/>.

## Options considered

| Option | Complexity | Security/operations | Decision |
|---|---:|---|---|
| External OIDC + API resource server (selected) | Medium | IdP owns MFA/recovery/key lifecycle; app owns internal authorization and scope | Best fit for API-first Stage 2; provider-neutral configuration keeps deployment choice open. |
| External OIDC + browser BFF session | Medium-high | Strong browser posture; requires UI/session/CSRF contract not yet in scope | Defer until React/mobile client is planned. |
| Embedded Spring Authorization Server | High | Self-contained but turns AgriInsight into an identity provider | Fallback only if deployment cannot use an IdP; not MVP scope. |
| Local username/password | Low initially, high later | App owns password reset, MFA, abuse controls, and recovery | Reject for enterprise posture. |

## Chosen security model

`/api/v1/**` is a stateless bearer-token resource server. The app validates issuer + audience + signature + `exp`/`nbf`, then resolves `iss+sub` to an active internal profile. Roles/permissions/assignments come from PostgreSQL, so changing a grant or deactivating a profile does not wait for a JWT role claim refresh. Unknown claims never elevate access.

The HTTP rule is an exact public allowlist for health/readiness and development-only metadata, `/api/v1/me` for an authenticated enriched principal, then a registry of explicit HTTP method + `PathPattern` + minimum-permission entries for business routes, followed by `anyRequest().denyAll()`. An endpoint-inventory test rejects unregistered controller mappings; method checks and scope-aware services provide the next layers. A later browser BFF can reuse the identity/authorization modules without placing tokens in browser storage.

## Required tests

1. No token, malformed token, wrong issuer/audience/signature, expired/not-yet-valid token.
2. Unknown and disabled external identity.
3. Valid identity with each role and no role claim.
4. Permission denial, safe 401/403/hidden 404, CORS allowlist, and ProblemDetail shape.
5. No password/token/private key in source, persistence, structured logs, or response DTOs.

## Open decisions carried as configuration

- Production issuer/provider and exact claim names.
- MFA/step-up assurance claim and token TTL policy.
- Later browser client pattern (BFF session vs PKCE bearer).

These are deployment choices; they do not justify adding a local identity provider or password store to the milestone.
