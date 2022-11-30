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
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class TupleMetrics(
   ds: DataSource,
   private val relname: String,
   private val grouped: Boolean = true,
   private val interval: Duration = 1.minutes,
) : MeterBinder {

   private val logger = KotlinLogging.logger { }
   private val template = NamedParameterJdbcTemplate(ds)
   private val query = javaClass.getResourceAsStream("/tuples.sql").bufferedReader().readText()
   private val queryGrouped = javaClass.getResourceAsStream("/tuples_grouped.sql").bufferedReader().readText()

   override fun bindTo(registry: MeterRegistry) {

      val insertedTupleCounts = relnameGauge("quaestor.postgres.n_tup_ins", "Number of rows inserted", registry)

      val modifiedSinceAnalyzed = relnameGauge(
         "quaestor.postgres.n_mod_since_analyze",
         "Estimated number of rows modified since this table was last analyzed",
         registry
      )

      val deletedTupleCounts = relnameGauge("quaestor.postgres.n_tup_del", "Number of rows deleted", registry)

      val liveTupleCounts = relnameGauge("quaestor.postgres.n_live_tup", "Estimated number of live rows", registry)
      val deadTupleCounts = relnameGauge("quaestor.postgres.n_dead_tup", "Estimated number of dead rows", registry)

      val tupUpd = relnameGauge(
         "quaestor.postgres.n_tup_upd",
         "Number of rows updated (includes HOT updated rows)",
         registry
      )
      val tupHotUpd = relnameGauge("quaestor.postgres.n_tup_hot_upd", "Number of rows HOT updated", registry)

      val seqTupRead = relnameGauge(
         "quaestor.postgres.seq_tup_read",
         "Number of live rows fetched by sequential scans",
         registry
      )

      val idxTupFetch = relnameGauge(
         "quaestor.postgres.idx_tup_fetch",
         "Number of live rows fetched by index scans",
         registry
      )

      val insSinceVacuum = relnameGauge(
         "quaestor.postgres.n_ins_since_vacuum",
         "Estimated number of rows inserted since this table was last vacuumed",
         registry
      )

      GlobalScope.launch {
         while (isActive) {
            runCatching {
               delay(interval)
               runInterruptible(Dispatchers.IO) {
                  template.query(
                     if (grouped) queryGrouped else query,
                     if (grouped) EmptySqlParameterSource() else MapSqlParameterSource(mapOf("relname" to relname)),
                  ) { rs ->
                     val relname = rs.getString("relname")

                     liveTupleCounts(relname).set(rs.getLong("n_live_tup"))
                     insertedTupleCounts(relname).set(rs.getLong("n_tup_ins"))

                     deadTupleCounts(relname).set(rs.getLong("n_dead_tup"))
                     deletedTupleCounts(relname).set(rs.getLong("n_tup_del"))

                     tupUpd(relname).set(rs.getLong("n_tup_upd"))
                     tupHotUpd(relname).set(rs.getLong("n_tup_hot_upd"))

                     modifiedSinceAnalyzed(relname).set(rs.getLong("n_mod_since_analyze"))
                     insSinceVacuum(relname).set(rs.getLong("n_ins_since_vacuum"))

                     idxTupFetch(relname).set(rs.getLong("idx_tup_fetch"))
                     seqTupRead(relname).set(rs.getLong("seq_tup_read"))
                  }
               }
            }.onFailure { logger.warn(it) { "Error fetching tuple metrics" } }
         }
      }
   }
}
