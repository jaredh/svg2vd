import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    application
}

val mainClassName = "dev.hendry.svg2vd.Svg2VdKt"

group = rootProject.name
version = "0.3.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(libs.clikt)
    compileOnly(libs.android.tools.sdk.common)
    implementation(libs.android.tools.common)
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

application {
    mainClass = mainClassName
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = mainClassName
    }

    from(configurations.compileClasspath.map {
        it.files.map { file ->
            if (file.isDirectory) file else zipTree(file)
        }
    })

    exclude(
        "META-INF/*.RSA",
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/versions/9/module-info.class",
        "NOTICE"
    )
}
