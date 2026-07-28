\set ON_ERROR_STOP on

-- Run as agriinsight_migrator after V23 and before enabling the alert worker.
-- Each invocation updates at most 500 valid legacy rows and leaves rows completed by an earlier batch unchanged.
BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

WITH legacy_batch AS MATERIALIZED (
    SELECT id
      FROM public.realtime_operational_alerts
     WHERE source_occurred_at IS NULL
       AND (
            (policy_code = 'OUTBOX_PUBLISH_BACKLOG' AND source_event_id IS NULL)
            OR (policy_code IN ('REALTIME_DELIVERY_LAG', 'REALTIME_DLT_RECORD')
                AND source_event_id IS NOT NULL)
       )
     ORDER BY id
     LIMIT 500
     FOR UPDATE SKIP LOCKED
),
backfilled AS (
    UPDATE public.realtime_operational_alerts AS alert
       SET source_occurred_at = alert.opened_at
      FROM legacy_batch AS legacy
     WHERE alert.id = legacy.id
       AND alert.source_occurred_at IS NULL
    RETURNING 1
)
SELECT count(*) AS rows_backfilled
  FROM backfilled;

COMMIT;

-- Do not enable the worker until both results are false. A true invalid-shape result
-- requires operator correction or retirement; this script intentionally does not rewrite source_event_id.
SELECT EXISTS (
    SELECT 1
      FROM public.realtime_operational_alerts
     WHERE source_occurred_at IS NULL
) AS legacy_source_occurred_at_rows_remain,
EXISTS (
    SELECT 1
      FROM public.realtime_operational_alerts
     WHERE (policy_code = 'OUTBOX_PUBLISH_BACKLOG' AND source_event_id IS NOT NULL)
        OR (policy_code IN ('REALTIME_DELIVERY_LAG', 'REALTIME_DLT_RECORD')
            AND source_event_id IS NULL)
) AS invalid_source_evidence_shape_rows_remain;
