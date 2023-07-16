package com.sksamuel.signum.dynamodb

import io.kotest.core.extensions.install
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.TestContainerExtension
import io.kotest.matchers.shouldBe
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

   test("request timer metrics by operation") {

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

      registry.get("signum.dynamodb.requests.timer").tags(
         "operation",
         "GetItem",
         "client_type",
         "SYNC",
         "success",
         "true"
      ).timer().count() shouldBe 1L

      registry.get("signum.dynamodb.requests.timer").tags(
         "operation",
         "PutItem",
         "client_type",
         "SYNC",
         "success",
         "true"
      ).timer().count() shouldBe 1L

      registry.get("signum.dynamodb.requests.timer").tags(
         "operation",
         "CreateTable",
         "client_type",
         "SYNC",
         "success",
         "true"
      ).timer().count() shouldBe 1L
   }

   test("request sizes") {

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
            .tableName("mytable2")
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
            .tableName("mytable2")
            .item(mapOf("key" to AttributeValue.fromS("a"), "range" to AttributeValue.fromN("1")))
            .build()
      ).sdkHttpResponse().isSuccessful shouldBe true

      client.getItem(
         GetItemRequest
            .builder()
            .tableName("mytable2")
            .key(mapOf("key" to AttributeValue.fromS("a"), "range" to AttributeValue.fromN("1"))).build()
      ).item() shouldBe mapOf("key" to AttributeValue.fromS("a"), "range" to AttributeValue.fromN("1"))

      registry.get("signum.dynamodb.requests.size").tags(
         "operation",
         "CreateTable",
         "client_type",
         "SYNC",
      ).gauge().value() shouldBe 311.0

      registry.get("signum.dynamodb.requests.size").tags(
         "operation",
         "PutItem",
         "client_type",
         "SYNC",
      ).gauge().value() shouldBe 67.0

      registry.get("signum.dynamodb.requests.size").tags(
         "operation",
         "GetItem",
         "client_type",
         "SYNC",
      ).gauge().value() shouldBe 66.0
   }
})
