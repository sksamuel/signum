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
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.2")
      implementation("io.micrometer:micrometer-core:1.11.1")
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
