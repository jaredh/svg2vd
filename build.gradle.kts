@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val mainClassName = "dev.hendry.svg2vd.Svg2VdKt"

group = rootProject.name
version = "0.5.0"

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
            compileOnly(libs.android.tools.sdk.common)
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

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("build") {
    dependsOn("fatJar")
}
