SELECT wait_event, wait_event_type, count(*) as count
FROM pg_stat_activity
group by wait_event, wait_event_type
