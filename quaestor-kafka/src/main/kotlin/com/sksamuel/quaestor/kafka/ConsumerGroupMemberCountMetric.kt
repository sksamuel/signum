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
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * Sends consumer group member counts as [Gauge] to a Micrometer [MeterRegistry] for one or more
 * consumer groups at a given cadence.
 *
 * @param consumerGroupIds the consumer group(s) to retrieve member counts for.
 * @param tags arbitrary key value pairs to add to these metrics.
 * @param interval how often to update the gauge.
 * @param createClientFn a function used to create the Kafka [AdminClient]
 */
class ConsumerGroupMemberCountMetric(
   private val consumerGroupIds: Set<String>,
   private val tags: Set<Tag>,
   private val interval: java.time.Duration,
   private val createClientFn: () -> AdminClient,
) : MeterBinder, AutoCloseable {

   companion object {
      const val MetricName = "kafka.consumer.group.members"
      const val MetricDescription = "Kafka consumer group member count"
   }

   private val logger = KotlinLogging.logger { }
   private var gauges = emptyMap<String, AtomicInteger>()
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
            ConsumerGroupClient(client).members(consumerGroupIds)
               .onSuccess { setMemberCounts(it) }
               .onFailure { logger.error(it) { "Error fetching kafka consumer lag" } }
         }
      }.onFailure { logger.error(it) { "Error running offsets fetch" } }
   }

   private fun setMemberCounts(it: Map<String, Int>) {
      it.forEach { (consumerGroupId, count) -> gauges[consumerGroupId]?.set(count) }
   }

   override fun bindTo(registry: MeterRegistry) {
      start()
      gauges = consumerGroupIds.associateWith {
         val counter = AtomicInteger()
         createGauge(it, counter).register(registry)
         counter
      }
   }

   private fun createGauge(consumerGroupId: String, counter: AtomicInteger): Gauge.Builder<Supplier<Number>> {
      return Gauge.builder(MetricName) { counter.get() }
         .description(MetricDescription)
         .tags(listOf(ImmutableTag("consumerGroup", consumerGroupId)) + tags)
         .strongReference(true)
   }

   override fun close() {
      future?.cancel(true)
   }
}
