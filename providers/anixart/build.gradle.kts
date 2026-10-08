plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.zenith.provider.sdk)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.zenith.provider.sdk.testkit)
}

tasks.jar {
    archiveFileName.set("provider.jar")
}
