@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val mainClassName = "dev.hendry.svg2vd.Svg2VdKt"

group = rootProject.name
version = "0.5.1"

repositories {
    google()
    mavenCentral()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexplicit-backing-fields")
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        binaries {
            executable {
                mainClass.set(mainClassName)
            }
        }
    }

    js(IR) {
        browser {
            webpackTask {
                mainOutputFileName.set("svg2vd.js")
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies { }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmMain.dependencies {
            implementation(libs.clikt)
            implementation(libs.android.tools.sdk.common)
            implementation(libs.android.tools.common)
        }

        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.android.tools.sdk.common)
            implementation(kotlin("test-junit"))
        }
    }
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Creates a fat JAR with all dependencies"

    manifest {
        attributes["Main-Class"] = mainClassName
    }

    from(kotlin.jvm().compilations["main"].output.allOutputs)
    from(configurations.named("jvmRuntimeClasspath").map {
        it.files.map { file ->
            if (file.isDirectory) file else zipTree(file)
        }
    })

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("build") {
    dependsOn("fatJar")
}

val generatedSrcDir = layout.buildDirectory.dir("generated/src/commonMain/kotlin")

tasks.register("generateBuildConfig") {
    val outputDir = generatedSrcDir
    val versionString = version.toString()
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.resolve("dev/hendry/svg2vd")
        dir.mkdirs()
        dir.resolve("BuildConfig.kt").writeText(
            """
            |package dev.hendry.svg2vd
            |
            |object BuildConfig {
            |    const val VERSION = "$versionString"
            |}
            """.trimMargin()
        )
    }
}

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generatedSrcDir)
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn("generateBuildConfig")
}
