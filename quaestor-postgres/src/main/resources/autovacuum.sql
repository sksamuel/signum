select last_autovacuum,
       autovacuum_count,
       autoanalyze_count
from pg_stat_user_tables
WHERE relname LIKE :relname
