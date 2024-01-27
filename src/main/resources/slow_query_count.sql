SELECT count(*)
FROM pg_stat_activity
WHERE (NOW() - pg_stat_activity.query_start) > INTERVAL ':::threshold minutes'
  AND state != 'idle'
