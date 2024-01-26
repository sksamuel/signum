@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "OPT_IN_USAGE")

package com.sksamuel.signum.postgres

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
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import kotlin.time.Duration

class QueryMetrics(
   ds: DataSource,
   private val threshold: Duration,
   private val interval: Duration?,
) : MeterBinder {

   private val template = NamedParameterJdbcTemplate(ds)
   private val query = javaClass.getResourceAsStream("/query.sql").bufferedReader().readText()
      .replace(":::threshold", threshold.inWholeMinutes.toString()   )

   override fun bindTo(registry: MeterRegistry) {

      val slowQueryCount: AtomicLong = gauge(
         "signum.postgres.slow_query_count",
         "Shows a count of queries slower than ${threshold.inWholeSeconds}s",
         registry
      )

      suspend fun query(): Result<Unit> = runCatching {
         runInterruptible(Dispatchers.IO) {
            template.query(
               query,
            ) { rs ->
               slowQueryCount.set(rs.getLong(1))
            }
         }
      }

      if (interval == null) {
         runBlocking {
            query()
         }
      } else {
         GlobalScope.launch {
            while (isActive) {
               delay(interval)
               query()
            }
         }
      }
   }
}
