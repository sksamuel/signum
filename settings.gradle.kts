rootProject.name = "quaestor"

plugins {
   id("de.fayard.refreshVersions") version "0.50.0"
}

refreshVersions {
   enableBuildSrcLibs()
}

refreshVersions {
   enableBuildSrcLibs()
   rejectVersionIf {
      candidate.stabilityLevel != de.fayard.refreshVersions.core.StabilityLevel.Stable
   }
}

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
   ":quaestor-postgres",
)
