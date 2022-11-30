@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "OPT_IN_USAGE")

package com.sksamuel.quaestor.postgres

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import mu.KotlinLogging
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class StatioMetrics(
   ds: DataSource,
   private val relname: String? = null,
   private val minsize: Int = 100000,
   private val interval: Duration = 1.minutes,
) : MeterBinder {

   private val logger = KotlinLogging.logger { }
   private val template = NamedParameterJdbcTemplate(ds)
   private val query = javaClass.getResourceAsStream("/statio.sql").bufferedReader().readText()

   override fun bindTo(registry: MeterRegistry) {

      val heapBlksRead = relnameGauge(
         "quaestor.postgres.heap_blks_read",
         "Number of disk blocks read from this table",
         registry
      )

      val heapBlksHit = relnameGauge(
         "quaestor.postgres.heap_blks_hit",
         "Number of buffer hits in this table",
         registry
      )

      val idxBlksRead = relnameGauge(
         "quaestor.postgres.idx_blks_read",
         "Number of disk blocks read from all indexes on this table",
         registry
      )

      val idxBlksHit = relnameGauge(
         "quaestor.postgres.idx_blks_hit",
         "Number of buffer hits in all indexes on this table",
         registry
      )

      val toastBlksRead = relnameGauge(
         "quaestor.postgres.toast_blks_read",
         "Number of disk blocks read from this table's TOAST table",
         registry
      )

      val toastBlksHit = relnameGauge(
         "quaestor.postgres.toast_blks_hit",
         "Number of buffer hits in this table's TOAST table",
         registry
      )

      val tidxBlksRead = relnameGauge(
         "quaestor.postgres.tidx_blks_read",
         "Number of disk blocks read from this table's TOAST table indexes",
         registry
      )

      val tidxBlksHit = relnameGauge(
         "quaestor.postgres.tidx_blks_hit",
         "Number of buffer hits in this table's TOAST table indexes",
         registry
      )

      GlobalScope.launch {
         while (isActive) {
            runCatching {
               delay(interval)
               runInterruptible(Dispatchers.IO) {
                  template.query(
                     query,
                     MapSqlParameterSource(mapOf("relname" to (relname ?: "%"))),
                  ) { rs ->
                     val index = rs.getString("index")
                     heapBlksRead(index).set(rs.getLong("heap_blks_read"))
                     heapBlksHit(index).set(rs.getLong("heap_blks_hit"))
                     idxBlksRead(index).set(rs.getLong("idx_blks_read"))
                     idxBlksHit(index).set(rs.getLong("idx_blks_hit"))
                     toastBlksRead(index).set(rs.getLong("toast_blks_read"))
                     toastBlksHit(index).set(rs.getLong("toast_blks_hit"))
                     tidxBlksRead(index).set(rs.getLong("tidx_blks_read"))
                     tidxBlksHit(index).set(rs.getLong("tidx_blks_hit"))
                  }
               }
            }.onFailure { logger.warn(it) { "Error fetching statio metrics" } }
         }
      }
   }
}
