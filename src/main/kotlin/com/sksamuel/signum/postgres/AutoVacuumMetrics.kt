@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "OPT_IN_USAGE")

package com.sksamuel.signum.postgres

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import kotlin.time.Duration

/**
 * Adds autovacuum and autoanalyze metrics for matching tables.
 * For each matching table, a metric is emitted with the table as a tag.
 *
 * @param ds the [DataSource] to run queries against
 * @param relname the relname query clause. Can include wildcards, eg %mytable%
 * @param interval the period to wait between running the metric queries in the database.
 *                 If null, then this will be a one time scan, which is useful if you want to run the metrics in a cron job rather than a long-running process.
 */
class AutoVacuumMetrics(
   ds: DataSource,
   private val relname: String,
   private val interval: Duration?,
) : MeterBinder {

   private val template = NamedParameterJdbcTemplate(ds)
   private val query = javaClass.getResourceAsStream("/autovacuum.sql").bufferedReader().readText()

   override fun bindTo(registry: MeterRegistry) {

      val autovacuumCounts = relnameGauge(
         "signum.postgres.autovacuum_count",
         "Total number of autovacuums on this relation",
         registry
      )

      val autoanalyzeCounts = relnameGauge(
         "signum.postgres.autoanalyze_count",
         "Total number of autoanalyzes on this relation",
         registry
      )

      val lastAutovacuumTimestamps = relnameGauge(
         "signum.postgres.last_autovacuum",
         "Timestamp in millis of when the last autovacuum occured",
         registry
      )

      val lastAutoanalyzeTimestamps = relnameGauge(
         "signum.postgres.last_autoanalyze",
         "Timestamp in millis of when the last autoanalyze occured",
         registry
      )

      val autoanalyzeOffset = relnameGauge(
         "signum.postgres.autoanalyze_offset",
         "Time in millis from now to when the last autoanalyze occured",
         registry
      )

      val autovacuumOffset = relnameGauge(
         "signum.postgres.autovacuum_offset",
         "Time in millis from now to when the last autovacuum occured",
         registry
      )

      val gauges = Gauges(
         autovacuumCounts,
         autovacuumOffset,
         autoanalyzeOffset,
         lastAutoanalyzeTimestamps,
         lastAutovacuumTimestamps,
         autoanalyzeCounts,
      )

      if (interval == null) {
         GlobalScope.launch {
            query(gauges)
         }
      } else {
         GlobalScope.launch {
            while (isActive) {
               delay(interval)
            }
         }
      }
   }

   data class Gauges(
      val autovacuumCounts: (String) -> AtomicLong,
      val autovacuumOffset: (String) -> AtomicLong,
      val autoanalyzeOffset: (String) -> AtomicLong,
      val lastAutoanalyzeTimestamps: (String) -> AtomicLong,
      val lastAutovacuumTimestamps: (String) -> AtomicLong,
      val autoanalyzeCounts: (String) -> AtomicLong
   )

   private suspend fun query(gauges: Gauges): Result<Unit> = runCatching {
      runInterruptible(Dispatchers.IO) {
         template.query(
            query,
            MapSqlParameterSource(mapOf("relname" to relname)),
         ) { rs ->
            val relname = rs.getString("relname")
            gauges.autovacuumCounts(relname).set(rs.getLong("autovacuum_count"))
            gauges.autoanalyzeCounts(relname).set(rs.getLong("autoanalyze_count"))

            val lastAutovacuum = rs.getTimestamp("last_autovacuum")
            if (lastAutovacuum == null) {
               gauges.lastAutovacuumTimestamps(relname).set(0)
               gauges.autovacuumOffset(relname).set(0)
            } else {
               gauges.lastAutovacuumTimestamps(relname).set(lastAutovacuum.time)
               gauges.autovacuumOffset(relname).set(System.currentTimeMillis() - lastAutovacuum.time)
            }

            val lastAutoanalyze = rs.getTimestamp("last_autoanalyze")
            if (lastAutoanalyze == null) {
               gauges.lastAutoanalyzeTimestamps(relname).set(0)
               gauges.autoanalyzeOffset(relname).set(0)
            } else {
               gauges.lastAutoanalyzeTimestamps(relname).set(lastAutoanalyze.time)
               gauges.autoanalyzeOffset(relname).set(System.currentTimeMillis() - lastAutoanalyze.time)
            }
         }
      }
   }
}
