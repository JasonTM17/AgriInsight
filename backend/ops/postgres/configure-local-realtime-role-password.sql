\set ON_ERROR_STOP on

-- Local Compose only. The value arrives through a psql variable and never lives in source.
ALTER ROLE agriinsight_realtime PASSWORD :'realtime_password';
