pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "shuffle-move"

// :core    - headless simulator + config parsing (no java.awt / javax.swing), shared by desktop and Android
// :desktop - the original Swing application, built from the remaining sources in src/
// :android:vision - board recognition in plain Kotlin (JVM-testable)
// :android:app - Android app: screen capture, overlay, UI (uses :core and :android:vision)
include(":core", ":desktop", ":android:vision", ":android:app")
