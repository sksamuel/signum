dependencies {
   api(libs.awssdk.s3)
   testApi(libs.bundles.testing)
}

apply("../publish.gradle.kts")
