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

class IndexMetrics(
   ds: DataSource,
   private val relname: String? = null,
   private val minsize: Int = 100000,
   private val interval: Duration = 5.minutes,
) : MeterBinder {

   private val logger = KotlinLogging.logger { }
   private val template = NamedParameterJdbcTemplate(ds)
   private val query = javaClass.getResourceAsStream("/index.sql").bufferedReader().readText()

   override fun bindTo(registry: MeterRegistry) {

      val indexSize = relnameGauge(
         "quaestor.postgres.index_size",
         "Disk space usage for the main fork of the specified index",
         registry
      )

      val idxTupRead = relnameGauge(
         "quaestor.postgres.idx_tup_read",
         "Number of live table rows fetched by simple index scans using this index",
         registry
      )

      val idxTupFetch = relnameGauge(
         "quaestor.postgres.idx_tup_fetch",
         "Disk space usage for the main fork of the specified index",
         registry
      )

      val idxScan = relnameGauge(
         "quaestor.postgres.idx_scan",
         "Number of index scans initiated on this index",
         registry
      )

      GlobalScope.launch {
         while (isActive) {
            runCatching {
               delay(interval)
               runInterruptible(Dispatchers.IO) {
                  template.query(
                     query,
                     MapSqlParameterSource(
                        mapOf(
                           "relname" to (relname ?: "%"),
                           "minsize" to minsize,
                        )
                     ),
                  ) { rs ->
                     val index = rs.getString("index")
                     indexSize(index).set(rs.getLong("index_size"))
                     idxTupRead(index).set(rs.getLong("idx_tup_read"))
                     idxTupFetch(index).set(rs.getLong("idx_tup_fetch"))
                     idxScan(index).set(rs.getLong("idx_scan"))
                  }
               }
            }.onFailure { logger.warn(it) { "Error fetching index metrics" } }
         }
      }
   }
}
