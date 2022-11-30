@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "OPT_IN_USAGE")

package com.sksamuel.quaestor.postgres

import io.micrometer.core.instrument.Gauge
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
import kotlin.time.Duration.Companion.minutes

class LockMetrics(
   ds: DataSource,
   private val relname: String,
   private val interval: Duration = 1.minutes,
) : MeterBinder {

   private val template = NamedParameterJdbcTemplate(ds)
   private val query = javaClass.getResourceAsStream("/fast_path_locks.sql").bufferedReader().readText()

   override fun bindTo(registry: MeterRegistry) {

      val fastPathLocks = AtomicLong(0).also {
         Gauge
            .builder("quaestor.postgres.locks.fastpath") { it }
            .description("The total number of fastpath locks")
            .tag("relname", relname)
            .tag("fastpath", "true")
            .register(registry)
      }

      val nonFastPathLocks = AtomicLong(0).also {
         Gauge
            .builder("quaestor.postgres.locks.fastpath") { it }
            .description("The total number of non-fastpath locks")
            .tag("relname", relname)
            .tag("fastpath", "false")
            .register(registry)
      }

      GlobalScope.launch {
         while (isActive) {
            runCatching {
               delay(interval)
               runInterruptible(Dispatchers.IO) {
                  template.query(
                     query,
                     MapSqlParameterSource(mapOf("relname" to relname))
                  ) { rs ->
                     val fastpath = rs.getBoolean("fastpath")
                     val count = rs.getLong("count")
                     if (fastpath) fastPathLocks.set(count) else nonFastPathLocks.set(count)
                  }
               }
            }
         }
      }
   }
}
