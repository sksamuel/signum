select max(last_autovacuum),
       sum(autovacuum_count),
       sum(autoanalyze_count)
from pg_stat_user_tables
WHERE relname LIKE :relname
