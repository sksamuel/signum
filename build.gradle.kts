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
   kotlin("jvm").version("1.7.20")
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

   group = "com.sksamuel.quaestor"
   version = Ci.version

   dependencies {
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")
      implementation("io.micrometer:micrometer-core:1.10.1")
      implementation("io.github.microutils:kotlin-logging:3.0.4")
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
      kotlinOptions.jvmTarget = "11"
   }
}

apply("./publish.gradle.kts")
