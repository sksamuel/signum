package com.sksamuel.signum.kafka.clients

import kotlinx.coroutines.future.await
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsOptions
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.common.TopicPartition
import kotlin.math.max

internal class OffsetsClient(private val adminClient: AdminClient) {

   /**
    * Returns the [TopicPartitionTimestamp] for each partition of the given topic.
    */
   suspend fun timestamps(topics: Set<String>): Result<List<TopicPartitionTimestamp>> = runCatching {
      val tps = partitions(topics).getOrThrow()
      val maxTimestamps = maxTimestamps(tps).getOrThrow()
      tps.map {
         TopicPartitionTimestamp(
            topic = it.topic(),
            partition = it.partition(),
            timestamp = maxTimestamps.getOrElse(it) { 0 },
         )
      }
   }

   /**
    * Returns the [TopicPartitionOffsets] for each partition of the given topic(s) for the given consumer group.
    * If the given [consumerGroupId] is null, then no consumer offsets will be fetched.
    */
   suspend fun offsets(
      topics: Set<String>,
      consumerGroupId: String?
   ): Result<List<TopicPartitionOffsets>> = runCatching {

      val tps = partitions(topics).getOrThrow()
      val endOffsets = endOffsets(tps).getOrThrow()
      val startOffsets = startOffsets(tps).getOrThrow()
      val consumerOffsets = if (consumerGroupId == null)
         emptyMap()
      else
         consumerOffsets(tps, consumerGroupId).getOrThrow()

      tps.map {
         TopicPartitionOffsets(
            topic = it.topic(),
            partition = it.partition(),
            group = consumerGroupId,
            consumerOffset = consumerOffsets.getOrElse(it) { 0 },
            startOffset = startOffsets.getOrElse(it) { 0 },
            endOffset = endOffsets.getOrElse(it) { 0 },
         )
      }
   }

   /**
    * Returns the list of [TopicPartition]s for a given topic or topics.
    */
   private suspend fun partitions(topics: Set<String>): Result<List<TopicPartition>> = runCatching {
      adminClient.describeTopics(topics).allTopicNames().toCompletionStage().await().flatMap { (topic, desc) ->
         val ps = desc.partitions()
         ps.map { TopicPartition(topic, it.partition()) }
      }
   }

   /**
    * Returns the start offsets for each of the given [TopicPartition]s.
    */
   private suspend fun startOffsets(tps: List<TopicPartition>): Result<Map<TopicPartition, Long>> = runCatching {
      adminClient
         .listOffsets(tps.associateWith { OffsetSpec.earliest() })
         .all().toCompletionStage().await()
         .mapValues { it.value.offset() }
   }

   /**
    * Returns the end offsets for each of the given [TopicPartition]s.
    */
   private suspend fun endOffsets(tps: List<TopicPartition>): Result<Map<TopicPartition, Long>> = runCatching {
      adminClient
         .listOffsets(tps.associateWith { OffsetSpec.latest() })
         .all().toCompletionStage().await()
         .mapValues { it.value.offset() }
   }

   /**
    * Returns the end offsets for each of the given [TopicPartition]s.
    */
   private suspend fun maxTimestamps(tps: List<TopicPartition>): Result<Map<TopicPartition, Long>> = runCatching {
      adminClient
         .listOffsets(tps.associateWith { OffsetSpec.maxTimestamp() })
         .all().toCompletionStage().await()
         .mapValues { it.value.offset() }
   }

   /**
    * Returns the offsets for each of the given [TopicPartition]s for the given consumer [consumerGroupId].
    *
    *  Note that missing offsets (nothing yet committed) or failing offsets (eg negative) will be filtered.
    */
   private suspend fun consumerOffsets(
      partitions: List<TopicPartition>,
      consumerGroupId: String,
   ): Result<Map<TopicPartition, Long>> = runCatching {
      adminClient.listConsumerGroupOffsets(
         consumerGroupId,
         ListConsumerGroupOffsetsOptions().topicPartitions(partitions)
      ).partitionsToOffsetAndMetadata().toCompletionStage().await()
         // offset is null if the group has not committed an offset for that partition
         .filter { it.value != null }
         .filter { it.value.offset() >= 0 }
         .mapValues { it.value.offset() }
   }
}

/**
 * Contains the consumer offset, partition end offset
 * for a given (consumer group, partition, topic) tuple.
 */
data class TopicPartitionOffsets(
   val topic: String,
   val partition: Int,
   val group: String?,
   val consumerOffset: Long,
   val startOffset: Long,
   val endOffset: Long,
) {
   fun count() = max(0, endOffset - startOffset)
}

data class TopicPartitionTimestamp(
   val topic: String,
   val partition: Int,
   val timestamp: Long,
)
