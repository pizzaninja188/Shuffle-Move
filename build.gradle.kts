// Root build. Module builds live in core/, desktop/ and android/app/.
// The legacy Ant build (build/compile_source.xml) compiles all of src/ too, but its version-stamping
// step needs the Nashorn JavaScript engine (JDK 8-14), so Gradle is the supported build.

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// The repo's build/ folder holds the Ant scripts and jars, so the root project must never use it.
layout.buildDirectory.set(file(".gradle-build"))
