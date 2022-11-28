package com.sksamuel.quaestor.kafka

import com.sksamuel.quaestor.kafka.clients.TopicClient
import com.sksamuel.quaestor.kafka.clients.TopicPartitionInsync
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
 * Sends out-of-sync replica counts for a topic or topics as a [Gauge] to a
 * Micrometer [MeterRegistry] at a given cadence.
 *
 * @param topics the names of the topic to calculate insync counts for.
 *                Each topic will be sent as a separate gauge.
 * @param tags arbitrary key value pairs to add to these metrics.
 * @param interval how often to update the metric.
 * @param createClientFn a function used to create the Kafka [AdminClient]
 */
class OutOfSyncReplicasMetric(
   private val topics: Set<String>,
   private val tags: Set<Tag>,
   private val interval: java.time.Duration,
   private val createClientFn: () -> AdminClient,
) : MeterBinder, AutoCloseable {

   constructor(
      topics: Set<String>,
      interval: java.time.Duration,
      createClientFn: () -> AdminClient,
   ) : this(topics, emptySet(), interval, createClientFn)

   companion object {
      const val MetricName = "quaestor.kafka.topic.partitions.replicas.outofsync"
      const val MetricDescription = "xxx"
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
            TopicClient(client).insync(topics)
               .onSuccess { setIsr(it) }
               .onFailure { logger.error(it) { "Error fetching kafka insync" } }
         }
      }.onFailure { logger.error(it) { "Error running kafka insync" } }
   }

   private fun setIsr(replicas: List<TopicPartitionInsync>) {
      replicas.filterNot { it.insync }
         .groupBy { it.topic }
         .forEach { (topic, partitions) ->
            val outOfSyncCount = partitions.sumOf { (it.replicas - it.isr).size }
            gauges[topic]?.set(outOfSyncCount)
         }
   }

   override fun bindTo(registry: MeterRegistry) {
      start()
      gauges = topics.associateWith {
         val counter = AtomicInteger()
         createGauge(it, counter).register(registry)
         counter
      }
   }

   private fun createGauge(topic: String, counter: AtomicInteger): Gauge.Builder<Supplier<Number>> {
      return Gauge.builder(MetricName) { counter.get() }
         .description(MetricDescription)
         .tags(listOf(ImmutableTag("topic", topic)) + tags)
         .strongReference(true)
   }

   override fun close() {
      future?.cancel(true)
   }
}
