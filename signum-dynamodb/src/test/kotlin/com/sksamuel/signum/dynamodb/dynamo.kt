package com.sksamuel.signum.dynamodb

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

val dockerImageName = DockerImageName.parse("amazon/dynamodb-local:1.20.0")
val dynamo = GenericContainer(dockerImageName).withExposedPorts(8000)
