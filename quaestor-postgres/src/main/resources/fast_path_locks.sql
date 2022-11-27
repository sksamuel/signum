SELECT count(*) as count, pl.fastpath as fastpath
FROM pg_locks pl
        LEFT JOIN pg_stat_activity psa
                  ON pl.pid = psa.pid
group by pl.fastpath
