\set ON_ERROR_STOP on

-- Local Compose only. Values arrive through psql variables and never live in source.
ALTER ROLE agriinsight_migrator PASSWORD :'migrator_password';
ALTER ROLE agriinsight_runtime PASSWORD :'runtime_password';
