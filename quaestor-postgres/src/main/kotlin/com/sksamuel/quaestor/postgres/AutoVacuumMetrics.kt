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
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import kotlin.coroutines.coroutineContext

class AutoVacuumMetrics(
   private val ds: DataSource,
   private val relname: String
) : MeterBinder {

   override fun bindTo(registry: MeterRegistry) {
      val autovacuumCount = AtomicLong(0).also {
         Gauge
            .builder("quaestor.postgres.autovacuum_count") { it }
            .description("Total number of autovacuums on this relation")
            .tag("relname", relname)
            .register(registry)
      }

      val lastAutovacuumTimestamp = AtomicLong(0).also {
         Gauge
            .builder("quaestor.postgres.last_autovacuum") { it }
            .description("Timestamp in millis of when the last autovacuum occured")
            .tag("relname", relname)
            .register(registry)
      }

      GlobalScope.launch {
         AutoVacuumTask(ds, relname, autovacuumCount, lastAutovacuumTimestamp).start()
      }
   }
}

class AutoVacuumTask(
   ds: DataSource,
   private val relname: String,
   private val autovacuumCount: AtomicLong,
   private val lastAutovacuumGauge: AtomicLong,
) {

   private val template = NamedParameterJdbcTemplate(ds)
   private val query = javaClass.getResourceAsStream("/autovacuum.sql").bufferedReader().readText()

   suspend fun start() {
      while (coroutineContext.isActive) {
         runCatching {
            runInterruptible(Dispatchers.IO) {
               template.query(
                  query,
                  MapSqlParameterSource(mapOf("relname" to relname))
               ) { rs ->

                  val count = rs.getLong("autovacuum_count")
                  autovacuumCount.set(count)

                  val ts = rs.getTimestamp("last_autovacuum")
                  lastAutovacuumGauge.set(ts.time)
               }
            }
         }
      }
   }
}
