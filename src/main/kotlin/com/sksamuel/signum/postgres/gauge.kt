package com.sksamuel.signum.postgres

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier

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

internal fun databaseGauge(
   name: String,
   description: String,
   registry: MeterRegistry,
   tags: List<Tag> = emptyList()
): (String) -> AtomicLong {
   val gauges = ConcurrentHashMap<String, AtomicLong>()
   return { datname ->
      gauges.getOrPut(datname) {
         AtomicLong(0L).also {
            Gauge
               .builder(name) { it }
               .description(description)
               .tag("datname", datname)
               .tags(tags)
               .strongReference(true)
               .register(registry)
         }
      }
   }
}

class SettableDouble : Supplier<Number> {

   private var double: Double = 0.0

   fun set(value: Double) {
      this.double = value
   }

   override fun get(): Double {
      return double
   }
}

internal fun relnameGaugeDouble(
   name: String,
   description: String,
   registry: MeterRegistry,
   tags: List<Tag> = emptyList()
): (String) -> SettableDouble {
   val gauges = ConcurrentHashMap<String, SettableDouble>()
   return { relname ->
      gauges.getOrPut(relname) {
         SettableDouble().also {
            Gauge
               .builder(name, it)
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
