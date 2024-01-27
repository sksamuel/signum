SELECT relation::regclass as relname, granted, COUNT(*) as count
FROM pg_locks
WHERE relation::regclass::text ~ :relname
group by relation, granted
