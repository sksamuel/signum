SELECT c.oid::regclass as relname,
       GREATEST(AGE(c.relfrozenxid), AGE(t.relfrozenxid))::NUMERIC as greatest
FROM pg_class c
        LEFT JOIN pg_class t ON c.reltoastrelid = t.oid
WHERE c.relkind IN ('r', 'm')
  AND c.relname like :relanme
ORDER BY 2 DESC
