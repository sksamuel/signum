package com.sksamuel.signum.kafka.clients

import kotlinx.coroutines.future.await
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.DescribeConfigsOptions
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.config.TopicConfig

internal class TopicClient(private val adminClient: AdminClient) {

   /**
    * Returns a count per topic of the number of non-preferred leaders.
    */
   suspend fun nonPreferredLeaders(topics: Set<String>): Result<Map<String, Int>> = runCatching {
      adminClient.describeTopics(topics).allTopicNames().toCompletionStage().await().map { (topic, desc) ->
         topic to desc.partitions().count { it.leader().id() != it.replicas().first().id() }
      }.toMap()
   }

   /**
    * Returns the retention periods in milliseconds for each of the given topics.
    */
   suspend fun retentions(topics: Set<String>): Result<Map<String, Long>> = runCatching {
      val configs = topics.map { ConfigResource(ConfigResource.Type.TOPIC, it) }
      adminClient.describeConfigs(
         configs,
         DescribeConfigsOptions().includeDocumentation(false).includeSynonyms(false)
      ).all()
         .toCompletionStage().await()
         .map {
            it.key.name() to (it.value.get(TopicConfig.RETENTION_MS_CONFIG)?.value()?.toLongOrNull() ?: 0)
         }.toMap()
   }

   /**
    * Returns the insync replica state for each of the partitions of the given topics.
    */
   suspend fun insync(topics: Set<String>): Result<List<TopicPartitionInsync>> = runCatching {
      adminClient.describeTopics(topics).allTopicNames().toCompletionStage().await().flatMap { (topic, desc) ->
         desc.partitions().map { info ->
            val replicas = info.replicas().map { it.id() }.toSet()
            val isr = info.isr().map { it.id() }.toSet()
            TopicPartitionInsync(
               topic,
               info.partition(),
               replicas,
               isr,
               replicas == isr,
            )
         }
      }
   }
}

class TopicPartitionInsync(
   val topic: String,
   val partition: Int,
   val replicas: Set<Int>,
   val isr: Set<Int>,
   val insync: Boolean,
)
