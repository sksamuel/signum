SELECT relid::regclass                        AS table,
       indexrelid::regclass                   AS index,
       PG_RELATION_SIZE(indexrelid::regclass) AS index_size,
       idx_tup_read,
       idx_tup_fetch,
       idx_scan
FROM pg_stat_user_indexes
        JOIN pg_index USING (indexrelid)
WHERE PG_RELATION_SIZE(indexrelid::regclass) > :minsize
