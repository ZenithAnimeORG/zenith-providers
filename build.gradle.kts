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
            val d8Exe = findD8Executable()

            val dexTask = if (d8Exe != null) {
                tasks.register<Exec>("dexProvider") {
                    dependsOn(tasks.named("jar"))
                    val jarTask = tasks.named<org.gradle.jvm.tasks.Jar>("jar")
                    val dexOutputDir = layout.buildDirectory.dir("dex")

                    doFirst {
                        dexOutputDir.get().asFile.mkdirs()
                    }

                    inputs.file(jarTask.map { it.archiveFile })
                    outputs.file(dexOutputDir.map { it.file("classes.dex") })

                    executable(d8Exe.absolutePath)
                    argumentProviders.add(CommandLineArgumentProvider {
                        listOf(
                            "--min-api", "26",
                            "--output", dexOutputDir.get().asFile.absolutePath,
                            jarTask.get().archiveFile.get().asFile.absolutePath,
                        )
                    })
                }
            } else null

            tasks.register<Zip>("assembleZpk") {
                dependsOn(tasks.named("jar"))
                if (dexTask != null) {
                    dependsOn(dexTask)
                }
                archiveFileName.set("${project.name}.zpk")
                destinationDirectory.set(rootProject.layout.buildDirectory.dir("distributions"))

                from(tasks.named("jar")) {
                    rename { "provider.jar" }
                }
                if (dexTask != null) {
                    from(layout.buildDirectory.dir("dex")) {
                        include("classes.dex")
                        rename { "provider.dex" }
                    }
                }
                from("manifest.json")
                from("settings.json")
                from("icon.png")
            }
        }
    }
}

fun findD8Executable(): File? {
    val candidates = listOfNotNull(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        System.getProperty("user.home") + "/Android/Sdk",
        "/usr/local/lib/android/sdk",
        "/opt/android-sdk",
    )
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val exeName = if (isWindows) "d8.bat" else "d8"

    for (candidate in candidates) {
        val buildTools = File(candidate, "build-tools")
        if (buildTools.isDirectory) {
            val toolDirs = buildTools.listFiles()?.filter { it.isDirectory }?.sortedDescending() ?: emptyList()
            for (dir in toolDirs) {
                val d8 = File(dir, exeName)
                if (d8.exists() && d8.canExecute()) {
                    return d8
                }
            }
        }
    }

    val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: emptyList()
    for (dir in pathDirs) {
        val d8 = File(dir, exeName)
        if (d8.exists() && d8.canExecute()) {
            return d8
        }
    }
    return null
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
