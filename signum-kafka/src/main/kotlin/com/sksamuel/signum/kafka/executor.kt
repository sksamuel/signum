package com.sksamuel.signum.kafka

import io.micrometer.core.instrument.util.NamedThreadFactory
import java.util.concurrent.Executors

// shared executor
internal val executor = Executors.newSingleThreadScheduledExecutor(NamedThreadFactory("signum-kafka"))
