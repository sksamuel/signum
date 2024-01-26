SELECT relname, reloptions
FROM pg_class
WHERE relname LIKE :relname
