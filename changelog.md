# Changelog

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
