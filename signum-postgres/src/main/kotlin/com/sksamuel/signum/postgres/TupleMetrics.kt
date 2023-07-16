@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "OPT_IN_USAGE")

package com.sksamuel.signum.postgres

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
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

   private val template = NamedParameterJdbcTemplate(ds)
   private val query = javaClass.getResourceAsStream("/tuples.sql").bufferedReader().readText()
   private val queryGrouped = javaClass.getResourceAsStream("/tuples_grouped.sql").bufferedReader().readText()

   override fun bindTo(registry: MeterRegistry) {

      val insertedTupleCounts = relnameGauge("signum.postgres.n_tup_ins", "Number of rows inserted", registry)

      val modifiedSinceAnalyzed = relnameGauge(
         "signum.postgres.n_mod_since_analyze",
         "Estimated number of rows modified since this table was last analyzed",
         registry
      )

      val deletedTupleCounts = relnameGauge("signum.postgres.n_tup_del", "Number of rows deleted", registry)

      val liveTupleCounts = relnameGauge("signum.postgres.n_live_tup", "Estimated number of live rows", registry)
      val deadTupleCounts = relnameGauge("signum.postgres.n_dead_tup", "Estimated number of dead rows", registry)

      val tupUpd = relnameGauge(
         "signum.postgres.n_tup_upd",
         "Number of rows updated (includes HOT updated rows)",
         registry
      )
      val tupHotUpd = relnameGauge("signum.postgres.n_tup_hot_upd", "Number of rows HOT updated", registry)

      val seqTupRead = relnameGauge(
         "signum.postgres.seq_tup_read",
         "Number of live rows fetched by sequential scans",
         registry
      )

      val idxTupFetch = relnameGauge(
         "signum.postgres.idx_tup_fetch",
         "Number of live rows fetched by index scans",
         registry
      )

      val insSinceVacuum = relnameGauge(
         "signum.postgres.n_ins_since_vacuum",
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
                     MapSqlParameterSource(mapOf("relname" to relname)),
                  ) { rs ->
                     val r = if (grouped) relname else rs.getString("relname")

                     liveTupleCounts(r).set(rs.getLong("n_live_tup"))
                     insertedTupleCounts(r).set(rs.getLong("n_tup_ins"))

                     deadTupleCounts(r).set(rs.getLong("n_dead_tup"))
                     deletedTupleCounts(r).set(rs.getLong("n_tup_del"))

                     tupUpd(r).set(rs.getLong("n_tup_upd"))
                     tupHotUpd(r).set(rs.getLong("n_tup_hot_upd"))

                     modifiedSinceAnalyzed(r).set(rs.getLong("n_mod_since_analyze"))
                     insSinceVacuum(r).set(rs.getLong("n_ins_since_vacuum"))

                     idxTupFetch(r).set(rs.getLong("idx_tup_fetch"))
                     seqTupRead(r).set(rs.getLong("seq_tup_read"))
                  }
               }
            }
         }
      }
   }
}
