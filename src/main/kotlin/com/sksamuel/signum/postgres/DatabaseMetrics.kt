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

class DatabaseMetrics(
   ds: DataSource,
   private val database: String,
   private val interval: Duration?,
) : MeterBinder {

   private val logger = KotlinLogging.logger { }
   private val template = NamedParameterJdbcTemplate(ds)
   private val query = javaClass.getResourceAsStream("/database.sql").bufferedReader().readText()

   override fun bindTo(registry: MeterRegistry) {

      val deadlocks = databaseGauge(
         "signum.postgres.deadlocks",
         "Number of deadlocks detected in this database", registry
      )

      val xact_commit = databaseGauge(
         "signum.postgres.xact_commit",
         "Number of transactions in this database that have been committed", registry
      )

      val xact_rollback = databaseGauge(
         "signum.postgres.xact_rollback",
         "Number of transactions in this database that have been rolled back", registry
      )

      suspend fun query() = runCatching {
         runInterruptible(Dispatchers.IO) {
            template.query(
               query,
               MapSqlParameterSource(mapOf("databasename" to database)),
            ) { rs ->
               val datname = rs.getString("datname")
               deadlocks(datname).set(rs.getLong("deadlocks"))
               xact_commit(datname).set(rs.getLong("xact_commit"))
               xact_rollback(datname).set(rs.getLong("xact_rollback"))
            }
         }
      }.onFailure { logger.warn(it) { "Error running query" } }

      if (interval == null) runBlocking {
         query()
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
