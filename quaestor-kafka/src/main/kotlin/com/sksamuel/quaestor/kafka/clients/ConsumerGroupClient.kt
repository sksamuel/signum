package com.sksamuel.quaestor.kafka.clients

import kotlinx.coroutines.future.await
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.common.ConsumerGroupState

internal class ConsumerGroupClient(private val adminClient: AdminClient) {

   suspend fun members(consumerGroupIds: Set<String>): Result<Map<String, Int>> = runCatching {
      adminClient.describeConsumerGroups(consumerGroupIds).all().toCompletionStage().await()
         .map { it.key to it.value.members().size }
         .toMap()
   }

   suspend fun states(consumerGroupIds: Set<String>): Result<Map<ConsumerGroupState, Int>> = runCatching {
      val desc = adminClient.describeConsumerGroups(consumerGroupIds).all().toCompletionStage().await()
      desc.values.groupBy { it.state() }.mapValues { it.value.size }
   }
}
