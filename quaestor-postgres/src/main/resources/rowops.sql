-- pg_class contains tables, sequences, indices and anythign else that is "table like"
select relname,
       n_live_tup,
       n_dead_tup,
       n_tup_ins,           -- Number of rows inserted
       n_tup_del,           -- Number of rows deleted
       n_mod_since_analyze, -- Estimated number of rows modified since this table was last analyzed
       n_ins_since_vacuum,  -- Estimated number of rows inserted since this table was last vacuumed
       seq_scan,            -- Number of sequential scans initiated on this table
       seq_tup_read,        -- Number of live rows fetched by sequential scans
       idx_scan,            -- Number of index scans initiated on this table
       idx_tup_fetch,       -- Number of live rows fetched by index scans
       n_tup_upd,           -- Number of rows updated (includes HOT updated rows)
       n_tup_hot_upd        -- Number of rows HOT updated
from pg_stat_user_tables
WHERE relname like :relname
