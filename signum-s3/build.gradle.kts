dependencies {
   val aws2 = "2.20.103"
   api("software.amazon.awssdk:s3:$aws2")
   api(libs.bundles.testing)
}

apply("../publish.gradle.kts")