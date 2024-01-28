# signum

Signum is a Kotlin library that provides metrics for postgres by scanning system tables and publishing the outputs to
Micrometer.

![master](https://github.com/sksamuel/signum/workflows/master/badge.svg)
[<img src="https://img.shields.io/maven-central/v/com.sksamuel.signum/signum.svg?label=latest%20release"/>](http://search.maven.org/#search%7Cga%7C1%7Csignum)
[<img src="https://img.shields.io/nexus/s/https/oss.sonatype.org/com.sksamuel.signum/signum.svg?label=latest%20snapshot&style=plastic"/>](https://oss.sonatype.org/content/repositories/snapshots/com/sksamuel/signum/)

For release see [changelog](changelog.md)

#### How to use

* Create a metrics instance
  from: `LockMetrics`, `TableMetrics`, `IndexMetrics`, `TupleMetrics`, `StatioMetrics`, `AutoVacuumMetrics`, `QueryMetrics`, `DatabaseMetrics`, `TransactionMetrics`
  passing in the datasource to use.
* Specify the table name (wildcards accepted), and specify an interval to re-run (or null for a one time shot).
* Bind to a Micrometer meter registry.

For example, to output metrics derived from the `TablesMetric` instance you would use the following code:

```kotlin
val metrics = TableMetrics(ds, "mytablename", null)
metrics.bindTo(registry)
```

#### Provided Metrics

| Metric Name                                    | Description                                                                  |
|------------------------------------------------|------------------------------------------------------------------------------|
| signum.postgres.n_tup_upd                      | Number of rows updated (includes HOT updated rows)                           |
| signum.postgres.n_tup_hot_upd                  | Number of rows HOT updated                                                   |
| signum.postgres.seq_tup_read                   | Number of live rows fetched by sequential scans                              |
| signum.postgres.idx_tup_fetch                  | Number of live rows fetched by index scans                                   |
| signum.postgres.n_ins_since_vacuum             | Estimated number of rows inserted since this table was last vacuumed         |
| signum.postgres.n_mod_since_analyze            | Estimated number of rows modified since this table was last analyzed         |
| signum.postgres.n_tup_del                      | Number of rows deleted                                                       |
| signum.postgres.n_live_tup                     | Estimated number of live rows                                                |
| signum.postgres.n_dead_tup                     | Estimated number of dead rows                                                |
| signum.postgres.n_tup_ins                      | Number of rows inserted                                                      |
| signum.postgres.pg_relation_size_main          | The size of the main data fork of the relation                               |
| signum.postgres.pg_relation_size_fsm           | The size of the Free Space Map                                               |
| signum.postgres.pg_relation_size_vm            | The size of the Visibility Map                                               |
| signum.postgres.pg_table_size                  | The size of the table                                                        |
| signum.postgres.pg_total_relation_size         | The size of the all relations summed                                         |
| signum.postgres.index_size                     | Disk space usage for the main fork of the specified index                    |
| signum.postgres.idx_tup_read                   | The number of index entries returned by scans on this index                  |
| signum.postgres.idx_tup_fetch                  | The number of live table rows fetched by simple index scans using this index |
| signum.postgres.idx_scan                       | Number of index scans initiated on this index                                |
| signum.postgres.locks.fastpath                 | The total number of fastpath locks                                           |
| signum.postgres.autovacuum_count               | Total number of autovacuums on this relation                                 |
| signum.postgres.autoanalyze_count              | Total number of autovacuums on this relation                                 |
| signum.postgres.last_autovacuum                | Timestamp in millis of when the last autovacuum occured                      |
| signum.postgres.last_autoanalyze               | Timestamp in millis of when the last autoanalyze occured                     |
| signum.postgres.autoanalyze_offset             | Time in millis from now to when the last autoanalyze occured                 |
| signum.postgres.autovacuum_offset              | Time in millis from now to when the last autovacuum occured                  |
| signum.postgres.heap_blks_read                 | Number of disk blocks read from this table                                   |
| signum.postgres.heap_blks_hit                  | Number of buffer hits in this table                                          |
| signum.postgres.idx_blks_read                  | Number of disk blocks read from all indexes on this table                    |
| signum.postgres.idx_blks_hit                   | Number of buffer hits in all indexes on this table                           |
| signum.postgres.toast_blks_read                | Number of disk blocks read from this table's TOAST table                     |
| signum.postgres.toast_blks_hit                 | Number of buffer hits in this table's TOAST table                            |
| signum.postgres.tidx_blks_read                 | Number of disk blocks read from this table's TOAST table indexes             |
| signum.postgres.tidx_blks_hit                  | Number of buffer hits in this table's TOAST table indexes                    |
| signum.postgres.query.waits                    | A count of the number of queries per wait event and wait event type          |
| signum.postgres.slow_query_count               | Shows a count of queries slower than a configurable duration                 |
| signum.postgres.fillfactor                     | Fillfactor per relation if exists                                            |
| signum.postgres.autovacuum_vacuum_scale_factor | Autovacuum scale factor per relation if global is overridden on that table   |
| signum.postgres.autovacuum_freeze_max_age      | Autovacuum freeze max age                                                    |
| signum.postgres.deadlocks                      | Per database deadlock count                                                  |
| signum.postgres.relfrozenxid                   | Per relation max age of the frozen xid                                       |
