---
phase: 3
title: Secure chat API and web UI
status: in-progress
priority: P1
effort: 2d
dependencies:
  - 2
---

# Phase 3: Secure chat API and web UI

## Overview

Expose the assistant through the internal analytics API and existing tokenless
Next.js BFF, then deliver a Field Ledger-native Vietnamese chat experience.

## Implementation Steps

1. Add authenticated internal query endpoint using the existing Spring-backed
   scope resolver; allow only approved analytics roles and preserve 401/403/404
   concealment rules.
2. Add BFF route with session auth, CSRF/origin/host validation, body and stream
   limits, abort propagation, correlation IDs, and sanitized upstream errors.
3. Create the assistant panel with suggested agricultural questions, progressive
   answer rendering, citation cards, data-as-of labels, refusal/error/retry
   states, keyboard support, focus management, and reduced motion.
4. Never render model HTML; treat answer and evidence as plain text and link
   citations only through allowlisted internal routes.
5. Keep conversations ephemeral in the first release; no browser local storage
   and no server persistence until retention/consent policy is approved.

## Success Criteria

- [ ] Direct analytics access and token-bearing browser calls remain impossible.
- [ ] Supplier and unauthorized FieldWorker journeys are denied; authorized
      personas receive only their farm/warehouse scope.
- [ ] Desktop, tablet, and mobile UI pass accessibility and responsive gates.
- [ ] Logout, session expiry, cancellation, double-submit, slow stream, and
      upstream outage have explicit states.

## Rollback

Hide the assistant navigation flag and disable the server route; no conversation
data requires migration or cleanup.
