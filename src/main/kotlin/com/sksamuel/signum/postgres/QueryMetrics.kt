@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "OPT_IN_USAGE")

package com.sksamuel.signum.postgres

import io.github.oshai.kotlinlogging.KotlinLogging
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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import kotlin.time.Duration

class QueryMetrics(
   ds: DataSource,
   private val threshold: Duration,
   private val interval: Duration?,
) : MeterBinder {

   private val logger = KotlinLogging.logger { }
   private val template = NamedParameterJdbcTemplate(ds)
   private val slowQueryQuery = javaClass.getResourceAsStream("/slow_query_count.sql").bufferedReader().readText()
      .replace(":::threshold", threshold.inWholeMinutes.toString())
   private val queryByWaits = javaClass.getResourceAsStream("/query_by_wait.sql").bufferedReader().readText()

   private val waitGauges = ConcurrentHashMap<Pair<String, String>, AtomicLong>()

   override fun bindTo(registry: MeterRegistry) {

      val slowQueryGauge: AtomicLong = gauge(
         name = "signum.postgres.slow_query_count",
         description = "Shows a count of queries slower than ${threshold.inWholeSeconds}s",
         registry = registry
      )

      fun waitGauge(waitEvent: String, waitEventType: String): AtomicLong {
         return waitGauges.getOrPut(Pair(waitEvent, waitEventType)) {
            AtomicLong(0).also {
               Gauge
                  .builder("signum.postgres.query.waits") { it }
                  .description("A count of the number of queries per wait event and wait event type")
                  .tag("waitEvent", waitEvent)
                  .tag("waitEventType", waitEventType)
                  .register(registry)
            }
         }
      }

      suspend fun queries(): Result<Unit> = runCatching {
         runInterruptible(Dispatchers.IO) {

            template.query(
               slowQueryQuery,
            ) { rs ->
               slowQueryGauge.set(rs.getLong(1))
            }

            template.query(
               queryByWaits,
            ) { rs ->
               val waitEvent = rs.getString("wait_event")
               val waitEventType = rs.getString("wait_event_type")
               val count = rs.getLong("count")
               waitGauge(waitEvent, waitEventType).set(count)
            }
         }
      }.onFailure { logger.warn(it) { "Error running query" } }

      if (interval == null) {
         runBlocking {
            queries()
         }
      } else {
         GlobalScope.launch {
            while (isActive) {
               delay(interval)
               queries()
            }
         }
      }
   }
}
