select max(last_autovacuum)   as last_autovacuum,
       sum(autovacuum_count)  as autovacuum_count,
       sum(autoanalyze_count) as autoanalyze_count,
       max(last_autoanalyze)  as last_autoanalyze
from pg_stat_user_tables
WHERE relname LIKE :relname