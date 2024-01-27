import org.gradle.api.tasks.testing.logging.TestExceptionFormat

buildscript {
  repositories {
    mavenCentral()
    maven {
      url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }
  }
}

plugins {
  signing
  `maven-publish`
  kotlin("jvm").version("1.8.21")
}

allprojects {
  apply(plugin = "org.jetbrains.kotlin.jvm")

  repositories {
    mavenLocal()
    mavenCentral()
    maven {
      url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots")
    }
  }

  group = "com.sksamuel.signum"
  version = Ci.version

  dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
    implementation("io.micrometer:micrometer-core:1.12.2")
    implementation("org.springframework:spring-jdbc:6.1.3")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.4")
  }

  tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
      showExceptions = true
      showStandardStreams = true
      exceptionFormat = TestExceptionFormat.FULL
    }
  }

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
  }
}

apply("./publish.gradle.kts")
