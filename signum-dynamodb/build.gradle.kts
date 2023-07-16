dependencies {
   api(libs.awssdk.dynamodb)
   testApi(libs.bundles.testing)
}

apply("../publish.gradle.kts")
