package com.sksamuel.signum.kafka

import com.sksamuel.signum.kafka.clients.OffsetsClient
import com.sksamuel.signum.kafka.clients.TopicPartitionOffsets
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
import kotlin.math.max

/**
 * Sends estimated record counts as a [Gauge] to a Micrometer [MeterRegistry] for a topic or topics.
 *
 * The count for a topic is calculated as the sum of each of the end offset - start offset for each partition.
 *
 * @param topics the name of the topic(s) to fetch lags for. Each topic is a separate gauge.
 * @param tags arbitrary key value pairs to add to these metrics.
 * @param interval how often to update the metric.
 * @param createClientFn a function used to create the Kafka [AdminClient]
 */
class TopicRecordCountMetric(
   private val topics: Set<String>,
   private val tags: Set<Tag>,
   private val interval: java.time.Duration,
   private val createClientFn: () -> AdminClient,
) : MeterBinder, AutoCloseable {

   companion object {
      const val MetricName = "signum.kafka.topic.records.count"
      const val MetricDescription = "Kafka topic estimated record count"
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
            OffsetsClient(client).offsets(topics, null)
               .onSuccess { setCounts(it) }
               .onFailure { logger.error(it) { "Error fetching kafka topic offsets" } }
         }
      }.onFailure { logger.error(it) { "Error running topic records fetch" } }
   }

   private fun setCounts(offsets: List<TopicPartitionOffsets>) {
      offsets.groupBy { it.topic }.forEach { (topic, offsets) ->
         val count = offsets.sumOf { max(it.endOffset - it.startOffset, 0) }
         gauges[topic]?.set(count)
      }
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
