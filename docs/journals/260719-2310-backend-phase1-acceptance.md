# Backend Phase 1: the wrapper checksum failure was an extraction problem

Date: 2026-07-19

## What happened

The first clean backend image build failed Maven Wrapper SHA-256 validation. The configured Maven 3.9.12 ZIP checksum was correct, and a separately downloaded official ZIP produced the same hash. The failure was therefore not a corrupt pin.

The Temurin 21 JDK builder did not contain `unzip`. Maven Wrapper 3.3.4 fell back from the configured ZIP to the `.tar.gz` distribution, then compared that archive against the ZIP checksum. The build failed exactly where a supply-chain guard should fail. It was frustrating because the error looked like checksum drift, while the actual defect was the builder's missing extraction capability.

## Decision

Install `unzip` only in the disposable build stage, before the first `./mvnw` execution, and remove the APT lists in the same layer. Keep the official ZIP URL and verified checksum so Windows and Linux continue to share one wrapper contract.

Rejected alternatives:

- Disabling the checksum would remove a real supply-chain control.
- Switching the project wrapper to `.tar.gz` would weaken Windows portability and change a working local contract.
- Replacing the wrapper with a floating Maven builder image would move version ownership outside the repository.

A delivery contract test now asserts that ZIP extraction is installed before the wrapper runs. The final image remains a separate Temurin JRE stage, so `unzip` is not shipped at runtime.

## Result

- Clean image build succeeded on Temurin 21.
- Guarded Maven verification passed 24 unit tests and 1 PostgreSQL/Flyway integration test.
- Runtime smoke proved non-root UID/GID `10001:10001`, a minimal `/app`, liveness `200`, and safe readiness `503` without a database.
- Analytics regression and packaging gates remained green.
- No image was pushed and no upstream PostgreSQL image was mirrored.

## Lesson

When a checksum mismatch appears, prove the bytes and extraction path before changing the checksum. A correct hash can expose an environmental branch in a bootstrap script; weakening verification would have hidden the actual defect.

## Next

Phase 2 can now start from an accepted foundation. Phase 7 still owns pinned release inputs, scanning, SBOM/provenance, immutable Docker Hub tags, and exact pushed-digest smoke tests.
