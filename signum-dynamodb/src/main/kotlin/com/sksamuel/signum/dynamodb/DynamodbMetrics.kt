package com.sksamuel.signum.dynamodb

import io.kotest.common.concurrentHashMap
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.MeterBinder
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import software.amazon.awssdk.core.ClientType
import software.amazon.awssdk.core.interceptor.Context
import software.amazon.awssdk.core.interceptor.ExecutionAttribute
import software.amazon.awssdk.core.interceptor.ExecutionAttributes
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

class DynamodbMetrics : MeterBinder, ExecutionInterceptor, AutoCloseable {

   companion object {
      val requestIdAttribute = ExecutionAttribute<String>("RequestId")
      val startTimeAttribute = ExecutionAttribute<Long>("StartTime")
   }

   private fun timer(opname: String, clientType: ClientType, status: Int) = Timer
      .builder("signum.dynamodb.request.timer")
      .tag("operation", opname)
      .tag("client_type", clientType.name)
      .tag("status", status.toString())
      .description("Dynamodb operation times")
      .register(registry)

   private val requestSizes = concurrentHashMap<Pair<String, ClientType>, AtomicLong>()
   private val responseSizes = concurrentHashMap<Pair<String, ClientType>, AtomicLong>()

   private fun requestSize(opname: String, clientType: ClientType): AtomicLong {
      return requestSizes.getOrPut(Pair(opname, clientType)) {
         val number = AtomicLong()
         Gauge.builder("signum.dynamodb.request.size") { number }
            .tag("operation", opname)
            .tag("client_type", clientType.name)
            .description("Dynamodb request sizes")
            .register(registry)
         number
      }
   }

   private fun responseSize(opname: String, clientType: ClientType): AtomicLong {
      return responseSizes.getOrPut(Pair(opname, clientType)) {
         val number = AtomicLong()
         Gauge.builder("signum.dynamodb.response.size") { number }
            .tag("operation", opname)
            .tag("client_type", clientType.name)
            .description("Dynamodb response sizes")
            .register(registry)
         number
      }
   }

   override fun close() {
      requestSizes.clear()
      responseSizes.clear()
   }

   private var registry: MeterRegistry = SimpleMeterRegistry()

   override fun bindTo(registry: MeterRegistry) {
      this.registry = registry
   }

   override fun beforeExecution(context: Context.BeforeExecution, executionAttributes: ExecutionAttributes) {
      executionAttributes.putAttribute(requestIdAttribute, UUID.randomUUID().toString())
      executionAttributes.putAttribute(startTimeAttribute, System.currentTimeMillis())
   }

   override fun beforeTransmission(context: Context.BeforeTransmission, executionAttributes: ExecutionAttributes) {
      val requestSize = context.requestBody().flatMap { it.optionalContentLength() }.getOrNull()
      if (requestSize != null) {
         val opname = executionAttributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME)
         val clientType = executionAttributes.getAttribute(SdkExecutionAttribute.CLIENT_TYPE)
         requestSize(opname, clientType).set(requestSize)
      }
   }

   override fun afterUnmarshalling(context: Context.AfterUnmarshalling, executionAttributes: ExecutionAttributes) {
      val responseSize = context.httpResponse().firstMatchingHeader("Content-Length").getOrNull()?.toLongOrNull()
      if (responseSize != null) {
         val opname = executionAttributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME)
         val clientType = executionAttributes.getAttribute(SdkExecutionAttribute.CLIENT_TYPE)
         responseSize(opname, clientType).set(responseSize)
      }
   }

   override fun afterExecution(context: Context.AfterExecution, executionAttributes: ExecutionAttributes) {
      val status = context.httpResponse().statusCode()
      val opname = executionAttributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME)
      val clientType = executionAttributes.getAttribute(SdkExecutionAttribute.CLIENT_TYPE)
      val time = System.currentTimeMillis() - executionAttributes.getAttribute(startTimeAttribute)
      timer(opname, clientType, status).record(time.milliseconds.toJavaDuration())
   }
}
