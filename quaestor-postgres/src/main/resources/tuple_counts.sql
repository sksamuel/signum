select relname,
       n_live_tup,
       n_dead_tup
from pg_stat_user_tables
WHERE relname LIKE :relname
