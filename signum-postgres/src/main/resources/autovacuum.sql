select relname,
       last_autovacuum,
       autovacuum_count,
       autoanalyze_count,
       last_autoanalyze
from pg_stat_user_tables
WHERE relname LIKE :relname
