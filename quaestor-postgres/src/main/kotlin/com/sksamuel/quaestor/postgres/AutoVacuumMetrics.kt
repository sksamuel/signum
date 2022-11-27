@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "OPT_IN_USAGE")

package com.sksamuel.quaestor.postgres

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import javax.sql.DataSource

class AutoVacuumMetrics(
   private val ds: DataSource,
   private val relname: String,
) : MeterBinder {

   private val template = NamedParameterJdbcTemplate(ds)
   private val query = javaClass.getResourceAsStream("/autovacuum.sql").bufferedReader().readText()

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

      GlobalScope.launch {
         while (isActive) {
            runCatching {
               runInterruptible(Dispatchers.IO) {
                  template.query(
                     query,
                     MapSqlParameterSource(mapOf("relname" to relname))
                  ) { rs ->
                     val relname = rs.getString("relname")
                     autovacuumCounts(relname).set(rs.getLong("autovacuum_count"))
                     autoanalyzeCounts(relname).set(rs.getLong("autoanalyze_count"))
                     lastAutovacuumTimestamps(relname).set(rs.getTimestamp("last_autovacuum").time)
                  }
               }
            }
         }
      }
   }
}
