---
title: External production readiness and controlled promotion
description: >-
  Close repository-owned promotion controls and collect owner-bound evidence
  before any external production deployment.
status: in-progress
priority: P1
effort: 8-12d plus external approvals
branch: main
tags:
  - infra
  - security
  - backend
  - frontend
  - critical
blockedBy: []
blocks: []
created: '2026-08-02T09:54:17.556Z'
createdBy: 'ck:plan'
source: skill
---

# External production readiness and controlled promotion

## Overview

Turn the verified internal release candidate into a defensible production
go/no-go decision. This plan does not deploy publicly until every external
control has an accountable owner, approval reference, and passing evidence.

## Scope challenge

- Existing: `v0.4.0` has passed exact-head CI and protected, paired Docker
  Hub/GHCR publication. The release workflow already scans, attests, smokes,
  and verifies digest parity for four first-party images.
- Minimum change: enforce immutable-image and exact-CI preconditions, add a
  machine-checked promotion evidence contract, refresh current-schema recovery
  proof, and make external ownership gaps explicit.
- Deferred: new product features, ML/RAG expansion, a self-hosted IdP, broker
  implementation, cloud-provider selection, and any attempt to invent legal or
  operational approvals in source code.
- Selected mode: HOLD SCOPE, hard + TDD. Four phases are justified by distinct
  supply-chain, recovery, governance, and runtime trust boundaries.

## Verified baseline

| Area | Current evidence | Boundary |
|---|---|---|
| Source quality | CI `30697294137` passed for commit `616527dcc7f4a03720fb48e617f9310ab9614873` | Internal CI is not a deployment approval |
| Registry release | Protected publication `30697808763` published `v0.4.0` to Docker Hub and GHCR | Registry parity is not public hosting |
| Identity/runtime | OIDC validation, deny-by-default routing, scoped RBAC/RLS and web session-key rollover exist | IdP/MFA contract and production origins remain owner-gated |
| Recovery | Hosted CI `30813839544` produced a checksum-linked V30 clean-target restore report with RLS smoke PASS | RPO/RTO and off-host controls remain open |
| External decision coordination | [Issue #22](https://github.com/JasonTM17/AgriInsight/issues/22) assigned to `JasonTM17`, response due `2026-08-10T10:00:00Z` | Coordination ownership is not production control ownership or approval |

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Promotion evidence and release controls](./phase-01-promotion-evidence-and-release-controls.md) | Completed |
| 2 | [Recovery and operational guardrails](./phase-02-recovery-and-operational-guardrails.md) | Completed |
| 3 | [External owner approvals](./phase-03-external-owner-approvals.md) | Pending |
| 4 | [Deployment validation and go-no-go](./phase-04-deployment-validation-and-go-no-go.md) | Pending |

## Dependencies

- Consume the completed internal candidate outputs from
  `../260722-2342-production-web-platform/` and the backend/realtime release
  evidence; do not reopen completed product scope.
- Phases 1 and 2 can prepare in parallel. Phase 3 records external approvals
  after their requested contracts are known. Phase 4 cannot start until all
  Phase 1-3 exit criteria and external environment access exist.

## Success criteria

- [ ] Every promotion uses four immutable `@sha256` image references that
  match a machine-validated evidence manifest and a successful exact-main CI.
- [x] A current-schema clean restore drill produces a measured, checksum-linked
  report without weakening RLS or deleting non-empty targets.
- [ ] Production IdP/MFA, broker, hosting/TLS, observability, recovery,
  credential rotation, registry visibility, and license decisions each have an
  owner, deadline, approval reference, and unlock criterion.
- [ ] Controlled deployment, authorization, broker recovery, restore,
  observability, rollback, security, integration, and browser gates produce an
  evidence package that supports a truthful GO or NO-GO decision.

## Non-negotiable boundaries

- No environment secrets, production backup, user data, token, or approval
  credential is committed.
- A missing external approval is a NO-GO, never a default value or workaround.
- Docker Hub and GHCR remain paired publication targets with equal immutable
  digests after all required release gates pass.
