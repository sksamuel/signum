select relation::regclass as relname, count(*) as count, mode
from pg_locks
where relation::regclass::text like :relname
  and locktype <> 'virtualxid'
  and locktype <> 'transactionid'
group by mode, relation
