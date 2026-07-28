# Codebase scout: realtime operational alert center

Date: 2026-07-28

## Verified starting point

- `D:\AgriInsight\backend\src\main\java\com\agriinsight\backend\realtime\api\RealtimeRoutes.java`
  registers exactly `GET /api/v1/realtime/summary` with `REALTIME_READ`.
- `RealtimeSummaryController` and `RealtimeSummaryService` return at most 100
  payload-free aggregate metric groups. They are not an event or alert feed.
- V18-V21 provide the outbox, event receipt, aggregate progress, tenant metric,
  RLS, index, and integration-role foundation. The realtime worker has
  cross-tenant metadata access by design but no business-table access.
- `CommandCommittedEvent`/v1 operational event parsing intentionally removes
  raw payload. It cannot safely evaluate low-stock, crop-health, work, or farm
  policies.
- No Java alert entity, migration, route, acknowledgement, or notification
  model exists.
- Python Gold `inventory_alerts`, `crop_health_alerts`, and risk data are
  snapshot analytics contracts. They must not become operational alert storage.

## UI and BFF evidence

- `D:\AgriInsight\web\src\components\app-shell\app-header.tsx` contains an
  inert, labelled bell button. It is a safe panel anchor, not an existing
  notification system.
- `D:\AgriInsight\web\src\server\bff\allowed-operation.ts` is an exact typed
  operation allowlist. `upstream-client.ts` holds upstream access and applies
  path/query/response restrictions.
- Existing Next product pages are server-loaded. No polling, SSE, WebSocket,
  or realtime feed contract currently exists.
- The existing batch Gold alerts in Overview, Inventory, Crop Health, and the
  local Streamlit dashboard must remain visibly source-separated from this
  operational transport feed.

## Implications

1. Implement only transport-health policies in this plan.
2. Extend the separate worker plus new metadata tables; never grant it
   inventory/work/farm access.
3. Create a new bounded backend/BFF contract rather than stretching the
   aggregate summary response.
4. Keep scope current-profile acknowledgement only; all tenant/user context is
   derived server-side.
5. Treat replay/dedupe, RLS, and DLT behavior as primary acceptance gates.
