@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "OPT_IN_USAGE")

package com.sksamuel.quaestor.postgres

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import javax.sql.DataSource

class TableSizeMetrics(
   ds: DataSource,
   private val relname: String,
) : MeterBinder {

   private val template = NamedParameterJdbcTemplate(ds)
   private val query = javaClass.getResourceAsStream("/table_size.sql").bufferedReader().readText()

   override fun bindTo(registry: MeterRegistry) {

      val pgRelationSizeMain = relnameGauge(
         "quaestor.postgres.pg_relation_size_main",
         "The size of the main data fork of the relation",
         registry
      )

      val pgRelationSizeFsm = relnameGauge(
         "quaestor.postgres.pg_relation_size_fsm",
         "The size of the Free Space Map",
         registry
      )

      val pgRelationSizeVm = relnameGauge(
         "quaestor.postgres.pg_relation_size_vm",
         "The size of the Visibility Map",
         registry
      )

      val pgTableSize = relnameGauge(
         "quaestor.postgres.pg_table_size",
         "The size of the Free Space Map",
         registry
      )

      val pgTotalRelationSize = relnameGauge(
         "quaestor.postgres.pg_total_relation_size",
         "The size of the Visibility Map",
         registry
      )

      GlobalScope.launch {
         while (isActive) {
            runCatching {
               runInterruptible(Dispatchers.IO) {
                  template.query(
                     query,
                     MapSqlParameterSource(mapOf("relname" to relname))
                  ) { rs ->
                     val relname = rs.getString("relname")

                     pgRelationSizeMain(relname).set(rs.getLong("pg_relation_size_main"))
                     pgRelationSizeFsm(relname).set(rs.getLong("pg_relation_size_fsm"))
                     pgRelationSizeVm(relname).set(rs.getLong("pg_relation_size_vm"))
                     pgTableSize(relname).set(rs.getLong("pg_table_size"))
                     pgTotalRelationSize(relname).set(rs.getLong("pg_total_relation_size"))
                  }
               }
            }
         }
      }
   }
}
