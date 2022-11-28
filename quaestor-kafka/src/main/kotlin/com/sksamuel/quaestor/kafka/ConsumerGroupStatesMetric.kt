package com.sksamuel.quaestor.kafka

import com.sksamuel.micrometer.kafka.clients.ConsumerGroupClient
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.ImmutableTag
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.common.ConsumerGroupState
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * Updates a gauge per consumer group state with the count of consumer groups in that state.
 *
 * @param consumerGroupIds the consumer group(s) to retrieve states for.
 * @param tags arbitrary key value pairs to add to these metrics.
 * @param interval how often to update the gauge.
 * @param createClientFn a function used to create the Kafka [AdminClient]
 */
class ConsumerGroupStatesMetric(
   private val consumerGroupIds: Set<String>,
   private val tags: Set<Tag>,
   private val interval: java.time.Duration,
   private val createClientFn: () -> AdminClient,
) : MeterBinder, AutoCloseable {

   constructor(
      consumerGroupIds: Set<String>,
      interval: java.time.Duration,
      createClientFn: () -> AdminClient,
   ) : this(consumerGroupIds, emptySet(), interval, createClientFn)

   companion object {
      const val MetricName = "kafka.consumer.group.states"
      const val MetricDescription = "Kafka consumer group states count"
   }

   private val logger = KotlinLogging.logger { }
   private var gauges = emptyMap<ConsumerGroupState, AtomicInteger>()
   private var future: ScheduledFuture<*>? = null

   private fun start() {
      future = executor.scheduleWithFixedDelay(
         { runBlocking { fetch() } },
         interval.toMillis(),
         interval.toMillis(),
         TimeUnit.MILLISECONDS
      )
   }

   private suspend fun fetch() {
      runCatching {
         createClientFn().use { client ->
            ConsumerGroupClient(client).states(consumerGroupIds)
               .onSuccess { setStates(it) }
               .onFailure { logger.error(it) { "Error fetching kafka consumer group states" } }
         }
      }.onFailure { logger.error(it) { "Error running topic consumer group states fetch" } }
   }

   private fun setStates(states: Map<ConsumerGroupState, Int>) {
      states.forEach { (state, count) -> gauges[state]?.set(count) }
   }

   override fun bindTo(registry: MeterRegistry) {
      start()
      gauges = ConsumerGroupState.values().associateWith {
         val counter = AtomicInteger()
         createGauge(it, counter).register(registry)
         counter
      }
   }

   private fun createGauge(state: ConsumerGroupState, counter: AtomicInteger): Gauge.Builder<Supplier<Number>> {
      return Gauge.builder(MetricName) { counter.get() }
         .description(MetricDescription)
         .tags(listOf(ImmutableTag("state", state.name)) + tags)
         .strongReference(true)
   }

   override fun close() {
      future?.cancel(true)
   }
}
