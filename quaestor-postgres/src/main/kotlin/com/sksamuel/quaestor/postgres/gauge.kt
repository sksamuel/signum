package com.sksamuel.quaestor.postgres

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal fun relnameGauge(name: String, description: String, registry: MeterRegistry): (String) -> AtomicLong {
   val gauges = ConcurrentHashMap<String, AtomicLong>()
   return { relname ->
      gauges.getOrPut(relname) {
         AtomicLong(0).also {
            Gauge
               .builder(name) { it }
               .description(description)
               .tag("relname", relname)
               .register(registry)
         }
      }
   }
}
