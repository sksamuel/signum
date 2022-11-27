@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "OPT_IN_USAGE")

package com.sksamuel.quaestor.postgres

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

class TableSizeMetrics(
   ds: DataSource,
   private val relname: String,
) : MeterBinder {

   private val template = NamedParameterJdbcTemplate(ds)
   private val query = javaClass.getResourceAsStream("/tuple_counts.sql").bufferedReader().readText()

   private fun relnameGauge(name: String, description: String, registry: MeterRegistry): (String) -> AtomicLong {
      val gauges = ConcurrentHashMap<String, AtomicLong>()
      return { relname ->
         gauges.getOrPut(relname) {
            AtomicLong(0).also {
               Gauge
                  .builder(name) { it }
                  .description(description)
                  .tag("relname", relname)
                  .register(registry)
            }
         }
      }
   }

   override fun bindTo(registry: MeterRegistry) {

      val liveTuplesCounts = relnameGauge("quaestor.postgres.n_live_tup", "Estimated number of live rows", registry)
      val deadTuplesCounts = relnameGauge("quaestor.postgres.n_dead_tup", "Estimated number of dead rows", registry)

      GlobalScope.launch {
         while (isActive) {
            runCatching {
               runInterruptible(Dispatchers.IO) {
                  template.query(
                     query,
                     MapSqlParameterSource(mapOf("relname" to relname))
                  ) { rs ->
                     val relname = rs.getString("relname")
                     liveTuplesCounts(relname).set(rs.getLong("n_live_tup"))
                     deadTuplesCounts(relname).set(rs.getLong("n_dead_tup"))
                  }
               }
            }
         }
      }
   }
}
