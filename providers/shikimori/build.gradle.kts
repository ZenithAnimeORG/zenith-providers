plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":providers:common"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.zenith.provider.sdk.testkit)
    testImplementation(libs.ktor.client.mock)
}

tasks.jar {
    archiveFileName.set("provider.jar")
    from(project(":providers:common").sourceSets.main.get().output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
