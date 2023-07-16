rootProject.name = "signum"

include(
   ":signum-dynamodb",
   ":signum-kafka",
   ":signum-postgres",
)

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
