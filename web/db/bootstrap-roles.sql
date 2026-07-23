\set ON_ERROR_STOP on

\if :{?web_migrator_password}
\else
  \echo 'web_migrator_password variable is required'
  \quit 3
\endif
\if :{?web_runtime_password}
\else
  \echo 'web_runtime_password variable is required'
  \quit 3
\endif

SELECT format(
  'CREATE ROLE agriinsight_web_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS'
) WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'agriinsight_web_owner') \gexec

SELECT format(
  'CREATE ROLE agriinsight_web_migrator LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS',
  :'web_migrator_password'
) WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'agriinsight_web_migrator') \gexec

SELECT format(
  'CREATE ROLE agriinsight_web_runtime LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS',
  :'web_runtime_password'
) WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'agriinsight_web_runtime') \gexec

ALTER ROLE agriinsight_web_migrator PASSWORD :'web_migrator_password';
ALTER ROLE agriinsight_web_runtime PASSWORD :'web_runtime_password';
GRANT agriinsight_web_owner TO agriinsight_web_migrator;
REVOKE agriinsight_web_owner FROM agriinsight_web_runtime;
SELECT format('GRANT CREATE ON DATABASE %I TO agriinsight_web_owner', current_database()) \gexec
SELECT format('GRANT CONNECT, CREATE ON DATABASE %I TO agriinsight_web_migrator', current_database()) \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO agriinsight_web_runtime', current_database()) \gexec
