# Phase 4 master data: authorization prechecks were not enough

Date: 2026-07-22

## What happened

The farm master-data work expanded from Farm into complete Field, Crop, and Season domain/application/persistence/HTTP slices. The result includes exact route registration, bounded queries, canonical DTOs, idempotent commands, ETag/If-Match, PostgreSQL RLS, V8 lifecycle triggers, and real concurrency tests.

The frustrating part was that the happy-path architecture looked correct while three boundary defects remained:

1. Field service allowed tenant-wide administration, but Field persistence demanded targeted FARM scope. A valid tenant administrator could pass authorization and still fail at the store.
2. Season accepted a 200-character variety name while PostgreSQL stored `VARCHAR(160)`. The API promised data the database could not persist.
3. FARM_MANAGER authorization was checked before a write, but the assignment row was not locked through commit. A concurrent revoke could land between precheck and mutation. Season create was worse: its direct `VALUES` insert did not include the assignment predicate at all.

These were not cosmetic review findings. They were production failure modes: false denials for administrators, database exceptions after API acceptance, and a time-of-check/time-of-use authorization race.

## Root cause

The service and SQL layers had similar-looking but different scope contracts. Review initially verified each layer in isolation instead of tracing one actor through permission resolution, parent visibility, idempotency claim, assignment state, SQL predicate, and commit.

The variety mismatch came from duplicating a length literal across schema, domain, commands, and API validation. The authorization race came from treating an `EXISTS` precheck as if it were a transaction guarantee. Under READ_COMMITTED, it is only a snapshot unless the relevant row is locked or the write itself carries the predicate.

## Decision

- `FarmScopeSql` now distinguishes tenant-wide and targeted write scope and locks an active FARM assignment with `FOR SHARE` until commit.
- Field and Season create stores return `Optional`; losing authorization/parent state becomes a safe hidden 404 or state conflict instead of an internal exception.
- Season create uses `INSERT ... SELECT` from the tenant/farm parent and applies the same scope predicate as reads and updates.
- Parent farm visibility is checked before an idempotency claim so an unassigned manager cannot create a command record for a hidden resource.
- `Season.VARIETY_NAME_MAX_LENGTH = 160` is the single Java contract matching V5.
- V7/V8 triggers remain the final lifecycle backstop, with tests covering parent-first and child-first races.

Rejected shortcuts:

- Letting the database exception become a generic 500: leaks implementation failure and breaks the API contract.
- Rechecking assignment without a lock: narrows the race window but does not close it.
- Giving FARM_MANAGER tenant-wide SQL scope: makes tests pass by weakening authorization.
- Increasing the database column to 200 without a product decision: changes an applied schema contract just to hide validation drift.

## Result

- Full backend `mvn verify`: `BUILD SUCCESS` in 02:56.
- Surefire report aggregate: 261 unit cases, zero failures/errors/skips.
- Completed Failsafe run: 53 integration cases, zero failures/errors/skips.
- Assignment-revocation lock, farm parent-first lifecycle, and Field/Crop/Season lifecycle races all pass on PostgreSQL 18.
- `.env` remains ignored; the safe repository scan found zero secret-match files outside dotenv/build/temp exclusions.
- Post-test free space stayed stable: C `16.92 GB`, D `29.80 GB`.

Independent reviewer/scout agents hit service quota. That was irritating because this was exactly the point where a second set of eyes was useful. The CK fallback was a manual two-pass review with executable race tests; the limitation is recorded instead of pretending an external review occurred.

## Lesson

Authorization is not proved by a service precheck. For a scoped write, follow the entire transaction and make the permission-bearing state stable until commit. Schema constraints must also have one named domain constant or a contract test; duplicated “close enough” limits eventually become a 500.

## Next steps

1. Implement Employee and Farm Assignment contracts before Activity APIs.
2. Decide whether an employee deactivation is rejected while active fields reference the employee or requires an audited responsibility-clear command.
3. Add the corresponding employee/field concurrency integration test before exposing employee lifecycle routes.
4. Keep frontend Stitch execution behind stable backend contracts and Docker Hub publication behind the Phase 7 scan/SBOM/provenance/digest gates.

## Unresolved Questions

- Active responsible-employee lifecycle rule: reject deactivation or require explicit reassignment/clear?
- Production OIDC/MFA contract and audit retention owner.
- Docker Hub namespace and protected release credentials.
