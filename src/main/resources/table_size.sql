-- pg_class contains tables, sequences, indices and anythign else that is "table like"
select u.relname,
       pg_relation_size(oid, 'main') as pg_relation_size_main,
       pg_relation_size(oid, 'vm')   as pg_relation_size_vm,
       pg_relation_size(oid, 'fsm')  as pg_relation_size_fsm,
       pg_table_size(oid)            as pg_table_size,
       pg_total_relation_size(oid)   as pg_total_relation_size
from pg_stat_user_tables u
        join pg_class pc on u.relid = pc.oid
WHERE u.relname like :relname
