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

ALTER ROLE agriinsight_web_migrator PASSWORD :'web_migrator_password';
ALTER ROLE agriinsight_web_runtime PASSWORD :'web_runtime_password';
