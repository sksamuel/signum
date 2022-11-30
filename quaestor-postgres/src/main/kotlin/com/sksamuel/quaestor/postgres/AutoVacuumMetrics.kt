@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "OPT_IN_USAGE")

package com.sksamuel.quaestor.postgres

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import mu.KotlinLogging
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Adds autovacuum and autoanalyze metrics.
 *
 * @param ds the [DataSource] to run queries against
 * @param relname the relname query clause. Can include wildcards, eg %mytable%
 * @param grouped responses for all results into a single metric. If false, then a separate metric for each relname in the resultset will be used.
 * @param interval the period to wait between running the metric queries in the database.
 */
class AutoVacuumMetrics(
   private val ds: DataSource,
   private val relname: String,
   private val grouped: Boolean = true,
   private val interval: Duration = 1.minutes,
) : MeterBinder {

   private val logger = KotlinLogging.logger { }
   private val template = NamedParameterJdbcTemplate(ds)
   private val query = javaClass.getResourceAsStream("/autovacuum.sql").bufferedReader().readText()
   private val queryGrouped = javaClass.getResourceAsStream("/autovacuum_grouped.sql").bufferedReader().readText()

   override fun bindTo(registry: MeterRegistry) {

      val autovacuumCounts = relnameGauge(
         "quaestor.postgres.autovacuum_count",
         "Total number of autovacuums on this relation",
         registry
      )

      val autoanalyzeCounts = relnameGauge(
         "quaestor.postgres.autoanalyze_count",
         "Total number of autoanalyzes on this relation",
         registry
      )

      val lastAutovacuumTimestamps = relnameGauge(
         "quaestor.postgres.last_autovacuum",
         "Timestamp in millis of when the last autovacuum occured",
         registry
      )

      val lastAutoanalyzeTimestamps = relnameGauge(
         "quaestor.postgres.last_autoanalyze",
         "Timestamp in millis of when the last autoanalyze occured",
         registry
      )

      val autoanalyzeOffset = relnameGauge(
         "quaestor.postgres.autoanalyze_offset",
         "Time in millis from now to when the last autoanalyze occured",
         registry
      )

      val autovacuumOffset = relnameGauge(
         "quaestor.postgres.autovacuum_offset",
         "Time in millis from now to when the last autovacuum occured",
         registry
      )

      GlobalScope.launch {
         while (isActive) {
            runCatching {
               delay(interval)
               runInterruptible(Dispatchers.IO) {
                  template.query(
                     if (grouped) queryGrouped else query,
                     MapSqlParameterSource(mapOf("relname" to relname)),
                  ) { rs ->
                     val r = if (grouped) relname else rs.getString("relname")
                     autovacuumCounts(r).set(rs.getLong("autovacuum_count"))
                     autoanalyzeCounts(r).set(rs.getLong("autoanalyze_count"))

                     val lastAutovacuum = rs.getTimestamp("last_autovacuum")
                     if (lastAutovacuum == null) {
                        lastAutovacuumTimestamps(r).set(0)
                        autovacuumOffset(r).set(0)
                     } else {
                        lastAutovacuumTimestamps(r).set(lastAutovacuum.time)
                        autovacuumOffset(r).set(System.currentTimeMillis() - lastAutovacuum.time)
                     }

                     val lastAutoanalyze = rs.getTimestamp("last_autoanalyze")
                     if (lastAutoanalyze == null) {
                        lastAutoanalyzeTimestamps(r).set(0)
                        autoanalyzeOffset(r).set(0)
                     } else {
                        lastAutoanalyzeTimestamps(r).set(lastAutoanalyze.time)
                        autoanalyzeOffset(r).set(System.currentTimeMillis() - lastAutoanalyze.time)
                     }
                  }
               }
            }.onFailure { logger.warn(it) { "Error fetching auto vacuum metrics" } }
         }
      }
   }
}
