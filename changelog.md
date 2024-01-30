# Changelog

### 1.2.7

* Exclude `transactionid` and `virtualxid` from lock mode counts

### 1.2.6

* Added `signum.postgres.relpages`: per relation relpage count
* Fixed use of like in 3 queries.

### 1.2.5

* Added `signum.postgres.relfrozenxid`: Max age of the frozen xid for the main table and toast table
* Added reloption: `autovacuum_vacuum_threshold`
* Added reloption: `autovacuum_vacuum_insert_threshold`
* Added reloption: `autovacuum_vacuum_insert_scale_factor`
* Fixed query to allow nulls in wait_events

### 1.2.4

* Added database level metrics:
  * `signum.postgres.deadlocks`: Number of deadlocks detected in this database
  * `signum.postgres.xact_commit`: Number of transactions in this database that have been committed
  * `signum.postgres.xact_rollback`: Number of transactions in this database that have been rolled back
* Added logging for broken queries using Kotlin Logging
* Added freeze max age from reloptions: `autovacuum_freeze_max_age`

### 1.2.3

* Added locks by grant type
* Added counts for queries by wait

### 1.2.2

* Added slow query count metric: `signum.postgres.slow_query_count`
* Added lock counts by mode: `signum.postgres.locks.modes`

### 1.2.1

* Added running vacuums as a gauge
* Added pg_class reloptions

### 1.2.0

* Changed interval to be nullable - if not specified scan runs once and exits. Useful for cron jobs.
* Made relname non-nullable. Statistics are only per table.
* Bumped micrometer, coroutine and jdbc template dependencies.

### 1.1.0

* Renamed to Signum
* Bumped to Kotlin 1.8

### 1.0.2

* Use strong references in MicroMeter 1.10

### 1.0.1

* Support grouping in postgres queries
* Added intervals to metric binders

### 1.0.0

* Initial release
