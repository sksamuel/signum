package com.sksamuel.quaestor.kafka

import com.sksamuel.quaestor.kafka.clients.TopicClient
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
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier

/**
 * Sends topic retention in milliseconds as a [Gauge] to a Micrometer [MeterRegistry] for a topic or topics
 * at a given cadence.
 *
 * @param topics the names of the topic to calculate retentions for.
 * @param tags arbitrary key value pairs to add to these metrics.
 * @param interval how often to update the metric.
 * @param createClientFn a function used to create the Kafka [AdminClient]
 */
class TopicRetentionMetric(
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
      const val MetricName = "kafka.topic.retention"
      const val MetricDescription = "Kafka topic retention time in milliseconds"
   }

   private val logger = KotlinLogging.logger { }
   private var gauges = emptyMap<String, AtomicLong>()
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
            TopicClient(client).retentions(topics)
               .onSuccess { setRetentions(it) }
               .onFailure { logger.error(it) { "Error fetching kafka topic retentions" } }
         }
      }.onFailure { logger.error(it) { "Error running topic retentions fetch" } }
   }

   private fun setRetentions(retentions: Map<String, Long>) {
      retentions.forEach { (topic, retention) -> gauges[topic]?.set(retention) }
   }

   override fun bindTo(registry: MeterRegistry) {
      start()
      gauges = topics.associateWith {
         val counter = AtomicLong()
         createGauge(it, counter).register(registry)
         counter
      }
   }

   private fun createGauge(topic: String, counter: AtomicLong): Gauge.Builder<Supplier<Number>> {
      return Gauge.builder(MetricName) { counter.get() }
         .description(MetricDescription)
         .tags(listOf(ImmutableTag("topic", topic)) + tags)
         .strongReference(true)
   }

   override fun close() {
      future?.cancel(true)
   }
}
