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

class TableSizeMetrics(
   ds: DataSource,
   private val relname: String,
   private val interval: Duration?,
) : MeterBinder {

   private val logger = KotlinLogging.logger { }
   private val template = NamedParameterJdbcTemplate(ds)
   private val sql = javaClass.getResourceAsStream("/table_size.sql").bufferedReader().readText()

   override fun bindTo(registry: MeterRegistry) {

      val pgRelationSizeMain = relnameGauge(
         "signum.postgres.pg_relation_size_main",
         "The size of the main data fork of the relation",
         registry
      )

      val pgRelationSizeFsm = relnameGauge(
         "signum.postgres.pg_relation_size_fsm",
         "The size of the Free Space Map",
         registry
      )

      val pgRelationSizeVm = relnameGauge(
         "signum.postgres.pg_relation_size_vm",
         "The size of the Visibility Map",
         registry
      )

      val pgTableSize = relnameGauge(
         "signum.postgres.pg_table_size",
         "The size of the table",
         registry
      )

      val pgTotalRelationSize = relnameGauge(
         "signum.postgres.pg_total_relation_size",
         "The size of the all relations summed",
         registry
      )

      suspend fun query() = runCatching {
         runInterruptible(Dispatchers.IO) {
            template.query(
               sql,
               MapSqlParameterSource(mapOf("relname" to relname)),
            ) { rs ->
               val relname = rs.getString("relname")
               pgRelationSizeMain(relname).set(rs.getLong("pg_relation_size_main"))
               pgRelationSizeFsm(relname).set(rs.getLong("pg_relation_size_fsm"))
               pgRelationSizeVm(relname).set(rs.getLong("pg_relation_size_vm"))
               pgTableSize(relname).set(rs.getLong("pg_table_size"))
               pgTotalRelationSize(relname).set(rs.getLong("pg_total_relation_size"))
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
