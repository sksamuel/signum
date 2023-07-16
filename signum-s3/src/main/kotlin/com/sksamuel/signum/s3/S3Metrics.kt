package com.sksamuel.signum.s3

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

class S3Metrics : MeterBinder, ExecutionInterceptor {

   companion object {
      val requestIdAttribute = ExecutionAttribute<String>("RequestId")
      val startTimeAttribute = ExecutionAttribute<Long>("StartTime")
   }

   private fun timer(opname: String, clientType: ClientType, status: Int) = Timer
      .builder("signum.s3.request.timer")
      .tag("operation", opname)
      .tag("client_type", clientType.name)
      .tag("status", status.toString())
      .description("S3 operation times")
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
      val status = context.httpResponse().statusCode()
      val opname = executionAttributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME)
      val clientType = executionAttributes.getAttribute(SdkExecutionAttribute.CLIENT_TYPE)
      val time = System.currentTimeMillis() - executionAttributes.getAttribute(startTimeAttribute)
      timer(opname, clientType, status).record(time.milliseconds.toJavaDuration())
   }
}
