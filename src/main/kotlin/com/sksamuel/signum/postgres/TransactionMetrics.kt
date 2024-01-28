@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "OPT_IN_USAGE")

package com.sksamuel.signum.postgres

import io.github.oshai.kotlinlogging.KotlinLogging
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
import javax.sql.DataSource
import kotlin.time.Duration

class TransactionMetrics(
   ds: DataSource,
   private val relname: String,
   private val interval: Duration?,
) : MeterBinder {

   private val logger = KotlinLogging.logger { }
   private val template = NamedParameterJdbcTemplate(ds)
   private val sql = javaClass.getResourceAsStream("/relfrozenxid.sql").bufferedReader().readText()

   override fun bindTo(registry: MeterRegistry) {

      val relfrozenxids = relnameGauge(
         "signum.postgres.relfrozenxid",
         "Max age of the frozen xid for the main table and toast table",
         registry
      )

      suspend fun query() = runCatching {
         runInterruptible(Dispatchers.IO) {
            template.query(
               sql,
               MapSqlParameterSource(mapOf("relname" to relname)),
            ) { rs ->
               val relname = rs.getString("relname")
               relfrozenxids(relname).set(rs.getLong("greatest"))
            }
         }
      }.onFailure { logger.warn(it) { "Error running query" } }

      if (interval == null) runBlocking { query() } else {
         GlobalScope.launch {
            while (isActive) {
               delay(interval)
               query()
            }
         }
      }
   }
}
