package com.sksamuel.signum.dynamodb

import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.TestContainerExtension
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import java.net.URI

class DynamodbMetricsTest : FunSpec({

   val ext = install(TestContainerExtension(dynamo))

   val dynamoEndpoint = "http://${ext.host}:" + ext.firstMappedPort
   val dynamoRegion = "local"

   test("timer metrics by operation") {

      val registry = SimpleMeterRegistry()
      val metrics = DynamodbMetrics()
      metrics.bindTo(registry)

      val client = DynamoDbClient.builder()
         .endpointOverride(URI.create(dynamoEndpoint))
         .region(Region.of(dynamoRegion))
         .overrideConfiguration(
            ClientOverrideConfiguration
               .builder()
               .addExecutionInterceptor(metrics)
               .build()
         ).build()

      client.createTable(
         CreateTableRequest
            .builder()
            .tableName("mytable")
            .keySchema(
               KeySchemaElement.builder().keyType(KeyType.HASH).attributeName("key").build(),
               KeySchemaElement.builder().keyType(KeyType.RANGE).attributeName("range").build(),
            )
            .attributeDefinitions(
               AttributeDefinition.builder().attributeName("key").attributeType(ScalarAttributeType.S).build(),
               AttributeDefinition.builder().attributeName("range").attributeType(ScalarAttributeType.N).build(),
            )
            .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(10).writeCapacityUnits(10).build())
            .build()
      ).sdkHttpResponse().isSuccessful shouldBe true

      client.putItem(
         PutItemRequest
            .builder()
            .tableName("mytable")
            .item(mapOf("key" to AttributeValue.fromS("a"), "range" to AttributeValue.fromN("1")))
            .build()
      ).sdkHttpResponse().isSuccessful shouldBe true

      client.getItem(
         GetItemRequest
            .builder()
            .tableName("mytable")
            .key(mapOf("key" to AttributeValue.fromS("a"), "range" to AttributeValue.fromN("1"))).build()
      ).item() shouldBe mapOf("key" to AttributeValue.fromS("a"), "range" to AttributeValue.fromN("1"))

      registry.timer(
         "signum.dynamodb.operations.timer",
         "operation",
         "GetItem",
         "client_type",
         "SYNC",
         "success",
         "true"
      ).count() shouldBe 1L

      registry.timer(
         "signum.dynamodb.operations.timer",
         "operation",
         "PutItem",
         "client_type",
         "SYNC",
         "success",
         "true"
      ).count() shouldBe 1L
   }

})
