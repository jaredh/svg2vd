import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.9.24"
    application
}

val mainClassName = "com.shopify.svg2vd.Svg2VdKt"

group = rootProject.name
version = "0.2"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation ("com.github.ajalt.clikt:clikt:5.0.1")

    compileOnly("com.android.tools:sdk-common:31.7.1")
    implementation("com.android.tools:common:31.7.1")
    testImplementation("junit", "junit", "4.12")
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
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
