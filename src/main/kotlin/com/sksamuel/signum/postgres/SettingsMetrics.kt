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
import kotlin.math.roundToLong
import kotlin.time.Duration

/**
 * Adds settings metrics.
 *
 * @param ds the [DataSource] to run queries against
 * @param relname the relname query clause. Can include wildcards, eg %mytable%
 * @param interval the period to wait between running the metric queries in the database.
 *                 If null, then this will be a one time scan, which is useful if you want to run the metrics in a cron job rather than a long-running process.
 */
class SettingsMetrics(
   ds: DataSource,
   private val relname: String,
   private val interval: Duration?,
) : MeterBinder {

   private val logger = KotlinLogging.logger { }
   private val template = NamedParameterJdbcTemplate(ds)
   private val query = javaClass.getResourceAsStream("/settings.sql").bufferedReader().readText()

   override fun bindTo(registry: MeterRegistry) {

      val fillfactor = relnameGaugeDouble(
         "signum.postgres.fillfactor",
         "Fillfactor",
         registry
      )

      val autovacuumScaleFactor = relnameGaugeDouble(
         "signum.postgres.autovacuum_vacuum_scale_factor",
         "Autovacuum scale factor",
         registry
      )

      val autovacuumFreezeMaxAge = relnameGaugeDouble(
         "signum.postgres.autovacuum_freeze_max_age",
         "Autovacuum freeze max age",
         registry
      )

      suspend fun query(): Result<Unit> = runCatching {
         runInterruptible(Dispatchers.IO) {
            template.query(
               query,
               MapSqlParameterSource(mapOf("relname" to relname)),
            ) { rs ->

               val relname = rs.getString("relname")
               val reloptions = rs.getString("reloptions")
               if (reloptions != null && reloptions.isNotBlank()) {

                  val options = reloptions.trim().removePrefix("{").removeSuffix("}").split(',').associate {
                     val entry = it.trim().split('=')
                     entry[0].trim() to entry[1].trim()
                  }

                  options["fillfactor"]?.toDoubleOrNull()
                     ?.let { fillfactor(relname).set(it) }

                  options["autovacuum_vacuum_scale_factor"]?.toDoubleOrNull()
                     ?.let { autovacuumScaleFactor(relname).set(it) }

                  options["autovacuum_freeze_max_age"]?.toDoubleOrNull()
                     ?.let { autovacuumFreezeMaxAge(relname).set(it) }
               }
            }
         }
      }.onFailure { logger.warn(it) { "Error running query" } }

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
