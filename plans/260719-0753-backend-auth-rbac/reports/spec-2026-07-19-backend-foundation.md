# Backend Phase 1 Spec Compliance

Date: 2026-07-19
Scope: current uncommitted `backend/`, `scripts/run-backend-tests.ps1`, Phase 1 plan, and supporting docs.

## Decision

Status: **NOT YET ACCEPTED**.

Foundation source is materially implemented and the Stage 2 findings have been remediated or explicitly adjudicated. Current runtime acceptance is still blocked: C is below the 10 GB PASS threshold, Docker is stopped, and no fresh Maven/Java 21/PostgreSQL/image run exists for the remediated tree. Prior pre-remediation Surefire output is historical evidence only, not a current green gate.

## Requirement Evidence

| Requirement | Current evidence | Verdict |
|---|---|---|
| Java 21 modular application | POM pins release 21; application is `@Modulithic`; boundary test exists | Implemented; current compile and Temurin 21 gate pending |
| Versioned API and deny-by-default security | `ApiVersion.PREFIX`; only exact health routes are public | Implemented; fresh MockMvc execution pending |
| Problem Detail and safe diagnostics | MVC and security handlers include validated correlation IDs; method validation has a 400 handler/test | Implemented; fresh execution pending |
| PostgreSQL/Flyway V1 | Migration and required Failsafe Testcontainers test exist | Implemented; Docker execution pending |
| Readiness vs liveness | production component registration is deterministic under `!test`; DB-down context asserts the real `schemaHistory` bean | Implemented; production-shaped execution pending |
| Tenant invariants | Java and PostgreSQL enforce UUID, ASCII code grammar, nonblank display name including canonical Unicode whitespace, UTC audit fields, and nonnegative version | Implemented; PostgreSQL execution pending |
| D-local guarded Maven | runner requires literal disk PASS, validates all output roots on D, rejects direct repo/tmp overrides | Implemented; fail-closed WARN behavior observed |
| False-green argument defense | runner parses `-D`, `--define`, and `--define=` forms; rejects output redirection, test skips, subset selectors, and `fail-never` | Implemented; parser execution pending |
| Hidden Maven channels | runner rejects inherited `MAVEN_ARGS`/`MAVEN_CONFIG`/`MAVEN_PROJECTBASEDIR`, unsafe JVM options (including `_JAVA_OPTIONS`), and project `.mvn/maven.config` | Implemented; environment-path execution pending |
| Test profile isolation | `application-test.yml` lives only in `src/test/resources` and cannot ship in the runtime jar | Implemented statically; fresh test/package execution pending |
| Bounded DB failure | Hikari plus PostgreSQL driver `connectTimeout`, `loginTimeout`, and `socketTimeout` are explicit and asserted in tests | Implemented statically; slow/black-hole runtime execution pending |
| Required integration lifecycle | Surefire excludes `*IntegrationTest`; Failsafe runs it in `verify`; Testcontainers no longer disables itself without Docker | Implemented; expected missing-Docker failure not executed due earlier disk gate |
| Non-root image | multi-stage Dockerfile uses UID/GID 10001 and explicit container bind | Implemented statically; build/runtime user pending |
| Existing analytics compatibility | Prior same-session Python tests, compileall, and Node syntax passed | No affected Python source; Compose and current full regression pending |

## Stage 2 Finding Adjudication

| Finding | Decision | Remediation |
|---|---|---|
| Ordering-unsafe `@ConditionalOnBean` | Accepted | Replaced with deterministic `@Profile("!test")`; production-shaped DB-down test asserts the actual bean exists |
| PostgreSQL test silently skips | Accepted | Split Maven lifecycle: unit test under Surefire, required container test under Failsafe `verify`; removed `disabledWithoutDocker` |
| `HandlerMethodValidationException` becomes 500 | Accepted | Added generic 400 mapping and constrained-request-parameter MockMvc test |
| SQL accepts whitespace-only display name | Accepted | Added the Java canonical whitespace set to PostgreSQL `btrim` and integration coverage |
| No-SQL-after-transaction test absent | Deferred as not applicable to Phase 1 | Phase 1 intentionally exposes no transactional business response. A synthetic route would not protect production. The first transactional API phase must add the focused SQL-count/serialization regression before acceptance |

## Static Validation on Current Tree

- Disk guard: C `9.006 GB` WARN; D `39.306 GB` PASS.
- POM XML parse: PASS.
- Main/test YAML parse: PASS.
- PowerShell parser: PASS.
- Java source size guard: PASS; no Java file exceeds 200 lines.
- TODO/FIXME/HACK scan: PASS.
- Docs validator: PASS; six documented Maven keys resolve through `.env.example`.
- `git diff --check`: PASS.

## Stage 3 Adversarial Review

The final adversarial review found no new Critical or High source defect. It rechecked the Stage 2 remediations, recorded the stale ignored local `backend/target/` as a clean-build requirement, and confirmed the runner rejects alternate Maven project/settings/module selectors. A strict goal allow-list remains optional future hardening. See [`adversarial-2026-07-19-backend-phase1.md`](./adversarial-2026-07-19-backend-phase1.md).

These checks do not replace Maven, PostgreSQL, Docker, or Java 21 execution.

## Acceptance Criteria Audit

- [ ] Temurin 21 image/verification build and local newer-JDK `--release 21` build.
- [ ] Fresh PostgreSQL 18 applies Flyway V1 and validates checksum/constraints.
- [ ] Current Modulith boundary test execution.
- [ ] Current DB-down/schema-behind readiness and process-only liveness execution.
- [ ] OSIV disabled execution evidence. No transactional business response exists in Phase 1; serialization/no-SQL becomes mandatory with the first one.
- [ ] Current Python, compileall, Node, and Compose regression.
- [ ] Non-root backend image build/smoke test with generated outputs confined to D.

## Blocking Gates

1. C remains below the project PASS threshold, so the wrapper correctly refuses to start Maven.
2. Docker daemon is stopped, so required Failsafe/PostgreSQL and image checks cannot run.
3. Java 21 execution evidence is missing for the current tree.

## Required Next Actions

1. Free enough C-drive capacity to reach at least 10 GB; prefer additional headroom.
2. Re-run the guarded `test`, then `verify`; missing Docker must be reported as a failed prerequisite.
3. With Docker available and both disks PASS, run PostgreSQL/Flyway, Compose config, Temurin 21 image build, and non-root smoke checks.
4. Run full analytics regression, CK adversarial review, and only then create focused commits.
5. Keep Phase 1 `in-progress` until the evidence above is recorded.

## Unresolved Questions

- Docker Hub namespace and repository policy remain a Phase 7 release decision.
- Production OIDC provider remains a Phase 2 decision.
