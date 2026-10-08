plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
}

dependencies {
    api(libs.zenith.provider.sdk)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)
    api(libs.ktor.client.core)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.serialization.json)
    api(libs.ktor.client.encoding)
    api(libs.ktorfit.lib)
    api(libs.ktorfit.annotations)
    api(libs.ksoup)
    api(libs.kermit)
    api(libs.okio)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.zenith.provider.sdk.testkit)
    testImplementation(libs.junit)
}
