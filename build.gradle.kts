plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktorfit) apply false
}

allprojects {
    group = "com.pilldev.zenith.providers"
    version = "1.0.0"
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }

        if (path != ":providers:common" && path != ":providers") {
            tasks.register<Zip>("assembleZpk") {
                dependsOn(tasks.named("jar"))
                archiveFileName.set("${project.name}.zpk")
                destinationDirectory.set(rootProject.layout.buildDirectory.dir("distributions"))

                from(tasks.named("jar")) {
                    rename { "provider.jar" }
                }
                from("manifest.json")
                from("settings.json")
                from("icon.png")
            }
        }
    }
}

val assembleAllZpk = tasks.register("assembleAllZpk") {
    dependsOn(subprojects.filter { it.path.startsWith(":providers:") && it.path != ":providers:common" }.map { "${it.path}:assembleZpk" })
}

tasks.register<Exec>("generateRepoIndex") {
    dependsOn(assembleAllZpk)
    workingDir = rootDir
    commandLine(
        "python3",
        "tools/zpk_tool.py",
        "index",
        "--repo-dir",
        "build/distributions",
        "--output",
        "build/distributions/index.json",
        "--base-url",
        "https://zenithanimeorg.github.io/zenith-providers",
    )
}
