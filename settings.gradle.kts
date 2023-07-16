rootProject.name = "signum"

include(
   ":signum-dynamodb",
   ":signum-kafka",
   ":signum-postgres",
)

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")


dependencyResolutionManagement {
   versionCatalogs {
      create("libs") {

         val kotest = "5.6.2"
         library("kotest-datatest", "io.kotest:kotest-framework-datatest:$kotest")
         library("kotest-junit5", "io.kotest:kotest-runner-junit5:$kotest")
         library("kotest-core", "io.kotest:kotest-assertions-core:$kotest")
         library("kotest-json", "io.kotest:kotest-assertions-json:$kotest")
         library("kotest-property", "io.kotest:kotest-property:$kotest")
         library("kotest-ktor", "io.kotest.extensions:kotest-assertions-ktor:2.0.0")
         library("kotest-testcontainers", "io.kotest.extensions:kotest-extensions-testcontainers:1.3.4")
         library("kotest-httpstub", "io.kotest.extensions:kotest-extensions-httpstub:1.0.1")
         library("testcontainers", "org.testcontainers:testcontainers:1.18.3")
         library("testcontainers-postgresql", "org.testcontainers:postgresql:1.18.3")

         bundle(
            "testing", listOf(
               "kotest-datatest",
               "kotest-junit5",
               "kotest-core",
               "kotest-json",
               "kotest-property",
               "kotest-ktor",
               "kotest-testcontainers",
               "kotest-httpstub",
               "testcontainers",
               "testcontainers-postgresql",
            )
         )
      }
   }
}
