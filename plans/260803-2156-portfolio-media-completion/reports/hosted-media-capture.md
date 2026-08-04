# Hosted media capture report

## Result

- Status: passed
- Workflow run: `30868766788`
- Run URL: <https://github.com/JasonTM17/AgriInsight/actions/runs/30868766788>
- Branch head: `0c8f3ff710a1907ca375577d0433eed20a4ba526`
- Tested merge commit: `2662928a6399a9be8ff9be7a60dfb2ae40b2b03d`
- Artifact: `portfolio-media-2662928a6399a9be8ff9be7a60dfb2ae40b2b03d`
- Imported by commit: `74458888`
- Post-import CI: `30870554268` (`success`)

The tested merge commit has branch head `0c8f3ff7` as its second parent. All
workflow jobs passed, including the real seven-persona browser gate and the
four image builds without registry publication. The subsequent CI run on the
media import commit also passed in full.

## Artifact verification

- Downloaded the immutable artifact through GitHub CLI into an isolated local
  review directory.
- Confirmed exactly 14 PNG capture sources, 14 optimized WebPs, and one catalog.
- Visually reviewed all seven desktop and seven mobile frames. No browser
  chrome, broken render, viewport overflow, secret, credential, accidental PII,
  or fabricated Assistant response found.
- Confirmed desktop outputs are `1280x800`; mobile outputs are `780x1688`.
- Confirmed each catalog byte size and SHA-256 against the imported binary.
- Confirmed the catalog repository, merge SHA, run ID, run URL, route, persona,
  viewport, and evidence boundary.
- Assistant captures the initial evidence-first workspace before any provider
  query and therefore makes no provider quality, latency, cost, or SLO claim.

## Validation

```text
python -m pytest tests/test_portfolio_media.py -q
3 passed
```

## Evidence boundary

These screenshots prove the real UI rendered against the hosted integration
stack for a portfolio/pre-production reference. They are not live production
telemetry, customer-farm records, agronomic ground truth, or production-readiness
evidence.

## Unresolved questions

None.
