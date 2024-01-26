package com.sksamuel.signum.postgres

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal fun relnameGauge(
   name: String,
   description: String,
   registry: MeterRegistry,
   tags: List<Tag> = emptyList()
): (String) -> AtomicLong {
   val gauges = ConcurrentHashMap<String, AtomicLong>()
   return { relname ->
      gauges.getOrPut(relname) {
         AtomicLong(0L).also {
            Gauge
               .builder(name) { it }
               .description(description)
               .tag("relname", relname)
               .tags(tags)
               .strongReference(true)
               .register(registry)
         }
      }
   }
}

internal fun gauge(
   name: String,
   description: String,
   registry: MeterRegistry,
   tags: List<Tag> = emptyList()
): AtomicLong {
   return AtomicLong(0L).also {
      Gauge
         .builder(name) { it }
         .description(description)
         .tags(tags)
         .strongReference(true)
         .register(registry)
   }
}
