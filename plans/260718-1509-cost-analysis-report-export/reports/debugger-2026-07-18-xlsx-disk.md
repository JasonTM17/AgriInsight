# XLSX adapter / C: disk investigation

## Executive Summary
- **Issue:** real XLSX adapter attempt in `2026-07-18 20:09-20:10` produced no surviving stdout artifact and no XLSX; user also observed C: free space drop from about `2.83 GB` to `1.15 GB`.
- **Impact:** XLSX artifact omitted; current C: free remains low at `1.38 GB` by direct check.
- **Root cause:** two separate findings. `No stdout artifact after the run` is expected by design because builder stdout/stderr are redirected into temp files inside a `TemporaryDirectory` and then removed on both success and failure. `Why no XLSX` cannot be proven exactly now; evidence supports a controlled builder failure before final QA JSON / XLSX save, but the adapter deletes the only per-run logs. `Why C: dropped` is not attributable to the adapter from current evidence; strongest competing evidence is coincident external Office/Word activity on C: in the same minute.
- **Status:** investigated; exact builder exception unrecoverable from surviving local evidence.
- **Safest next action:** do not rerun XLSX export until C: headroom is restored. On next controlled retry, persist builder stdout/stderr outside the temp subtree and force process temp/cache to D: if possible.

## Timeline
- `20:09:07` - `WINWORD.EXE` starts.
- `20:09:08` - `C:\Users\Admin\AppData\Local\Temp\{...}` and `OProcSessId.dat` created.
- `20:09:11` - `C:\Users\Admin\AppData\Local\Temp\VBE` created.
- `20:09:24` - `D:\AgriInsight\artifacts\_tmp\report-exports\xlsx-qa\adapter-temp` created.
- `20:09:57` - `adapter-temp` last modified; folder now empty.
- `20:10:26` - new `chrome.exe` renderer process starts.
- `20:10:46` - new Codex/docker helper processes start (`node_repl.exe`, `node.exe`, `docker.exe`, `docker-mcp.exe`).
- Investigation time - C: free measured at `1.38 GB`.

## Technical Analysis

### Evidence gathered
- `artifacts\_tmp\report-exports\xlsx-qa` currently contains only `adapter-temp`.
- `adapter-temp` has:
  - `CreationTime = 2026-07-18 20:09:24`
  - `LastWriteTime = 2026-07-18 20:09:57`
  - child count `0`
- Current C: free space:
  - `Get-PSDrive C` => `FreeGB = 1.38`
- Targeted event-log check:
  - `Application` log in `20:08-20:12` => no matching crash/hang events for `node.exe`, `python.exe`, `cmd.exe`, `artifact-tool`, `xlsx`, `excel`
  - `System` log targeted query => no matching events found
- Current git state:
  - `scripts/build-cost-report.mjs`
  - `src/agriinsight/cost_report_xlsx.py`
  - `tests/test_cost_report_xlsx.py`
  - all show `??` in `git status`; no commit history in this checkout to correlate

### Code-path facts
- `src/agriinsight/cost_report_xlsx.py:287-346`
  - creates a per-run `TemporaryDirectory` under caller-provided `temp_root`
  - writes `payload.json`, `builder-stdout.log`, `builder-stderr.log`, previews, `cost-report.xlsx` inside that temp subtree
  - redirects child `stdout`/`stderr` to files, not console (`stdout=stdout_file`, `stderr=stderr_file`)
  - always removes the temporary `node_modules` link in `finally`
- `scripts/build-cost-report.mjs:892-940`
  - builder writes the final JSON QA payload to `stdout` only after:
    1. payload validation
    2. workbook build
    3. `inspectAndRender(...)`
    4. `SpreadsheetFile.exportXlsx(workbook).save(outputPath)`
    5. non-empty output-file stat check
  - if any prior step fails, process writes JSON error to `stderr` and exits non-zero
- `scripts/build-cost-report.mjs:782-853`
  - `inspectAndRender(...)` performs workbook inspection and renders 6 PNG previews before final XLSX save
- `tests/test_cost_report_xlsx.py:34-99`
  - explicitly asserts `list(temp_root.iterdir()) == []` after success
  - explicitly asserts `list(temp_root.iterdir()) == []` after failure
- `src/agriinsight/cost_report_service.py:60-81,113-132`
  - XLSX is optional
  - `ExportUnavailable` becomes `xlsx_unavailable_reason`; bundle can still return CSV/PDF without XLSX

## Hypotheses

### Hypothesis 1: adapter temp cleanup failed or process was hard-killed mid-run
- **Test:** inspect surviving temp tree; compare with cleanup contract in code and tests.
- **Result:** eliminated.
- **Why:** if Python were killed before `TemporaryDirectory` cleanup, a `cost-report-*` child directory should still exist under `adapter-temp`. None exists. Repo test also proves `temp_root` is intentionally empty after both success and failure.

### Hypothesis 2: code path intentionally suppresses visible stdout and the run failed in a controlled way before final XLSX completion
- **Test:** inspect wrapper and builder output flow.
- **Result:** confirmed for `no visible/surviving stdout`; likely for `no XLSX`.
- **Why:** stdout never goes to console; it is redirected to `builder-stdout.log` inside the temp subtree. That subtree is deleted afterwards. So absence of persisted stdout after the run is expected. Missing XLSX means the Node builder most likely never reached `build-cost-report.mjs:927-934`, or Python rejected the result afterward. Exact exception cannot be recovered now because those temp logs were cleaned.

### Hypothesis 3: the adapter itself caused the C: free-space drop
- **Test:** inspect intentional write paths in code; inspect surviving repo temp state; inspect concurrent process activity on C: around the same minute.
- **Result:** not supported; weaker than external-activity hypothesis.
- **Why:** repo code intentionally writes payload, logs, previews, and workbook under D: temp root, not C:. `build-cost-report.mjs` also enforces output paths inside the working directory. No large surviving D: residue exists. In the same minute, separate Office/Word activity started on C: (`WINWORD.EXE` at `20:09:07`, Office AI helper processes at `20:09:10` and `20:09:17`, matching new Temp entries at `20:09:08-20:09:11`). Later `chrome.exe` and Codex/docker helpers started in `20:10:26-20:10:46`. With only `1.38 GB` free now, coincident temp/cache/page activity on C: is a stronger explanation than the adapter’s intentional filesystem writes.

## Findings
1. `No stdout artifact after the attempt` is expected by design, not a separate failure symptom. The wrapper redirects builder output to temp files and then deletes the temp subtree.
2. Adapter temp cleanup did occur. `adapter-temp` exists only as the caller-supplied root; the per-run subtree is gone.
3. The attempt did not run long enough to hit the wrapper’s 180-second timeout. Visible D: activity started at `20:09:24` and ended by `20:09:57`.
4. `No XLSX` is consistent with a controlled builder failure or post-build validation failure, but the exact failing step is unrecoverable because the adapter does not preserve `builder-stdout.log` / `builder-stderr.log` outside temp.
5. Current evidence does not prove the adapter caused the C: drop. The code’s intended write path is D:, while unrelated Office/Word and later browser/Codex activity occurred on C: in the same window.
6. Main design flaw exposed here is observability, not proven workbook logic defect: once the run ends, the only direct builder evidence is deleted.

## Recommendations

### Immediate (P0)
- Restore C: free headroom before any XLSX retry. With `1.38 GB` free, another render/preview attempt is high risk.
- Do not treat missing persisted stdout under `xlsx-qa` as evidence of a second failure; it is expected from current cleanup behavior.

### Short-term (P1)
- On the next controlled retry, preserve builder logs outside the `TemporaryDirectory`:
  - copy `builder-stdout.log` / `builder-stderr.log` to a stable report path before cleanup, or
  - add a debug mode that disables cleanup only for investigation.
- Force `TMP` / `TEMP` for the retry to a D: location if the runtime stack allows it, to reduce C: pressure and make disk attribution cleaner.

### Long-term (P2)
- Add structured telemetry around XLSX export:
  - start/end timestamps
  - child exit code
  - output bytes
  - preview count
  - bounded stderr excerpt on failure
- Add a low-disk preflight guard before XLSX render, especially before preview rendering.
- Preserve the `xlsx_unavailable_reason` in a durable application log, not only in-memory response state.

## Supporting Evidence
- `src/agriinsight/cost_report_xlsx.py:287-346`
- `scripts/build-cost-report.mjs:782-853`
- `scripts/build-cost-report.mjs:892-940`
- `tests/test_cost_report_xlsx.py:34-99`
- `src/agriinsight/cost_report_service.py:60-81`
- `src/agriinsight/cost_report_service.py:113-132`
- filesystem state:
  - `D:\AgriInsight\artifacts\_tmp\report-exports\xlsx-qa\adapter-temp` exists
  - child count `0`
  - created `20:09:24`, last written `20:09:57`
- process metadata in incident window:
  - `WINWORD.EXE` started `20:09:07`
  - Office AI helper processes started `20:09:10` and `20:09:17`
  - Temp entries on C: created `20:09:08-20:09:11`
  - `chrome.exe` renderer started `20:10:26`
  - Codex/docker helper processes started `20:10:46`

## Unresolved Questions
- What exact exception text was in `builder-stderr.log` for the failed attempt?
- Did any lower-level dependency used by `@oai/artifact-tool` allocate transient cache/temp data on C: despite repo-level D: output paths?
- What process consumed the net ~`1.4-1.7 GB` on C: between the user’s before/after measurements?

Status: DONE_WITH_CONCERNS
Summary: Proven that temp cleanup occurred and that missing persisted stdout is by design. Most likely XLSX path failed before final QA/output, but exact failure text is gone because the adapter deletes per-run logs. Current evidence does not tie the C: free-space drop to the adapter’s intentional file writes; coincident Office/Word activity on C: is the strongest competing explanation.
Concerns/Blockers: Exact builder stderr from the 20:09 run is unrecoverable from surviving local artifacts.
