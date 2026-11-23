plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Board recognition as plain Kotlin over int pixel arrays: no Android dependency, so it is unit
// tested on the JVM against synthetic screenshots made from Shuffle Move's real icons.

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // The real icons (Shuffle Move's bundled art) used to build test screenshots.
    testImplementation(project(":core"))
}
