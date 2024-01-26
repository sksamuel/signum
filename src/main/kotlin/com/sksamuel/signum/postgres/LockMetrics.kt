@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "OPT_IN_USAGE")

package com.sksamuel.signum.postgres

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import kotlin.time.Duration

/**
 * Adds metrics for lock fast path.
 */
class LockMetrics(
   ds: DataSource,
   private val relname: String,
   private val interval: Duration?,
) : MeterBinder {

   private val template = NamedParameterJdbcTemplate(ds)
   private val fastPathQuery = javaClass.getResourceAsStream("/fast_path_locks.sql").bufferedReader().readText()
   private val modesQuery = javaClass.getResourceAsStream("/locks_by_type.sql").bufferedReader().readText()
   private val fastPaths = ConcurrentHashMap<Pair<String, Boolean>, AtomicLong>()
   private val modes = ConcurrentHashMap<Pair<String, String>, AtomicLong>()

   override fun bindTo(registry: MeterRegistry) {

      fun fastPathGauge(mode: String, fastpath: Boolean): AtomicLong {
         return fastPaths.getOrPut(Pair(mode, fastpath)) {
            AtomicLong(0).also {
               Gauge
                  .builder("signum.postgres.locks.fastpath") { it }
                  .description("The total number of fastpath locks")
                  .tag("relname", relname)
                  .tag("fastpath", fastpath.toString())
                  .tag("mode", mode)
                  .register(registry)
            }
         }
      }

      fun modeGauges(mode: String, relname: String): AtomicLong {
         return modes.getOrPut(Pair(mode, relname)) {
            AtomicLong(0).also {
               Gauge
                  .builder("signum.postgres.locks.modes") { it }
                  .description("A count of the number of locks per mode")
                  .tag("relname", relname)
                  .tag("mode", mode)
                  .register(registry)
            }
         }
      }

      suspend fun query1() = runCatching {
         runInterruptible(Dispatchers.IO) {
            template.query(
               fastPathQuery,
               MapSqlParameterSource(mapOf("relname" to relname)),
            ) { rs ->
               val fastpath = rs.getBoolean("fastpath")
               val mode = rs.getString("mode")
               val count = rs.getLong("count")
               fastPathGauge(mode, fastpath).set(count)
            }
         }
      }

      suspend fun query2() = runCatching {
         runInterruptible(Dispatchers.IO) {
            template.query(
               modesQuery,
               MapSqlParameterSource(mapOf("relname" to relname)),
            ) { rs ->
               val mode = rs.getString("mode")
               val count = rs.getLong("count")
               val relname = rs.getString("relname")
               modeGauges(mode, relname).set(count)
            }
         }
      }

      if (interval == null) {
         runBlocking {
            query1()
            query2()
         }
      } else {
         GlobalScope.launch {
            while (isActive) {
               delay(interval)
               query1()
               query2()
            }
         }
      }
   }
}
