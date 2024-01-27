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

class StatioMetrics(
   ds: DataSource,
   private val relname: String,
   private val interval: Duration?,
) : MeterBinder {

   private val logger = KotlinLogging.logger { }
   private val template = NamedParameterJdbcTemplate(ds)
   private val query = javaClass.getResourceAsStream("/statio.sql").bufferedReader().readText()

   override fun bindTo(registry: MeterRegistry) {

      val heapBlksRead = relnameGauge(
         "signum.postgres.heap_blks_read",
         "Number of disk blocks read from this table",
         registry
      )

      val heapBlksHit = relnameGauge(
         "signum.postgres.heap_blks_hit",
         "Number of buffer hits in this table",
         registry
      )

      val idxBlksRead = relnameGauge(
         "signum.postgres.idx_blks_read",
         "Number of disk blocks read from all indexes on this table",
         registry
      )

      val idxBlksHit = relnameGauge(
         "signum.postgres.idx_blks_hit",
         "Number of buffer hits in all indexes on this table",
         registry
      )

      val toastBlksRead = relnameGauge(
         "signum.postgres.toast_blks_read",
         "Number of disk blocks read from this table's TOAST table",
         registry
      )

      val toastBlksHit = relnameGauge(
         "signum.postgres.toast_blks_hit",
         "Number of buffer hits in this table's TOAST table",
         registry
      )

      val tidxBlksRead = relnameGauge(
         "signum.postgres.tidx_blks_read",
         "Number of disk blocks read from this table's TOAST table indexes",
         registry
      )

      val tidxBlksHit = relnameGauge(
         "signum.postgres.tidx_blks_hit",
         "Number of buffer hits in this table's TOAST table indexes",
         registry
      )

      suspend fun query() = runCatching {
         runInterruptible(Dispatchers.IO) {
            template.query(
               query,
               MapSqlParameterSource(mapOf("relname" to (relname ?: "%"))),
            ) { rs ->
               val relname = rs.getString("relname")
               heapBlksRead(relname).set(rs.getLong("heap_blks_read"))
               heapBlksHit(relname).set(rs.getLong("heap_blks_hit"))
               idxBlksRead(relname).set(rs.getLong("idx_blks_read"))
               idxBlksHit(relname).set(rs.getLong("idx_blks_hit"))
               toastBlksRead(relname).set(rs.getLong("toast_blks_read"))
               toastBlksHit(relname).set(rs.getLong("toast_blks_hit"))
               tidxBlksRead(relname).set(rs.getLong("tidx_blks_read"))
               tidxBlksHit(relname).set(rs.getLong("tidx_blks_hit"))
            }
         }
      }.onFailure { logger.warn(it) { "Error running query" } }

      if (interval == null) {
         runBlocking { query() }
      } else {
         GlobalScope.launch {
            while (isActive) {
               runCatching {
                  delay(interval)
                  query()
               }
            }
         }
      }
   }
}
