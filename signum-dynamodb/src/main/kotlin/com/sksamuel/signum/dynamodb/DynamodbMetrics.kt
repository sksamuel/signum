package com.sksamuel.signum.dynamodb

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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

class DynamodbMetrics : MeterBinder, ExecutionInterceptor {

   private val requestIdAttribute = ExecutionAttribute<String>("RequestId")
   private val startTimeAttribute = ExecutionAttribute<Long>("StartTime")

   private fun timer(opname: String, clientType: ClientType) = Timer
      .builder("signum.dynamodb.operations.timer")
      .tag("operation", opname)
      .tag("client_type", clientType.name)
      .description("Timer by operation type")
      .register(registry)

   private var registry: MeterRegistry = SimpleMeterRegistry()

   override fun bindTo(registry: MeterRegistry) {
      this.registry = registry
   }

   override fun beforeExecution(context: Context.BeforeExecution, executionAttributes: ExecutionAttributes) {
      executionAttributes.putAttribute(requestIdAttribute, UUID.randomUUID().toString())
      executionAttributes.putAttribute(startTimeAttribute, System.currentTimeMillis())
   }

   override fun afterExecution(context: Context.AfterExecution, executionAttributes: ExecutionAttributes) {
      val opname = executionAttributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME)
      val clientType = executionAttributes.getAttribute(SdkExecutionAttribute.CLIENT_TYPE)
      val time = System.currentTimeMillis() - executionAttributes.getAttribute(startTimeAttribute)
      timer(opname, clientType).record(time.milliseconds.toJavaDuration())
   }
}
