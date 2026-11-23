plugins {
    `java-library`
}

/*
 * :core compiles a headless subset of the shared ../src tree in place, so the desktop app,
 * the Ant build and Android all use the same simulator sources.
 *
 * Everything listed here must stay free of java.awt / javax.swing / javax.imageio
 * (enforced by the checkHeadless task) so it can run on Android.
 */
val coreSources = listOf(
    "shuffle/fwk/GradingMode.java",
    "shuffle/fwk/EntryMode.java",
    "shuffle/fwk/EntryModeUser.java",
    "shuffle/fwk/config/**",
    "shuffle/fwk/data/**",
    "shuffle/fwk/headless/**",
    "shuffle/fwk/i18n/**",
    "shuffle/fwk/log/**",
)
val coreExcludes = listOf(
    "shuffle/fwk/config/manager/ImageManager.java",
    "shuffle/fwk/config/provider/ImageManagerProvider.java",
)
extra["coreSources"] = coreSources
extra["coreExcludes"] = coreExcludes

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options")
}

// ShuffleVersion.java is generated from build/ShuffleVersion.java_template, as the Ant build does.
val generateVersion by tasks.registering {
    val template = rootProject.file("build/ShuffleVersion.java_template")
    val readme = rootProject.file("README.md")
    val outDir = layout.buildDirectory.dir("generated/version")
    inputs.files(template, readme)
    outputs.dir(outDir)
    doLast {
        val m = Regex("""v(\d+)\.(\d+)\.(\d+)""").find(readme.readLines().first())
            ?: error("No version in first line of README.md")
        val (major, minor, sub) = m.destructured
        val text = template.readText(Charsets.UTF_8)
            .replace("BUILD_DATE_REPLACE", "0")
            .replace("VERSION_MAJOR_REPLACE", major)
            .replace("VERSION_MINOR_REPLACE", minor)
            .replace("VERSION_SUBMINOR_REPLACE", sub)
        val out = outDir.get().file("shuffle/fwk/ShuffleVersion.java").asFile
        out.parentFile.mkdirs()
        out.writeText(text, Charsets.UTF_8)
    }
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("../src"))
            srcDir(generateVersion)
            include(coreSources)
            include("shuffle/fwk/ShuffleVersion.java")
            exclude(coreExcludes)
        }
        resources {
            setSrcDirs(listOf("../resources", "../i18n"))
            exclude("docs/**", "img/splash.png", "config/buildReport.xml")
        }
    }
}

dependencies {
    api("org.apache.commons:commons-lang3:3.4")
    api("commons-io:commons-io:2.4")
    testImplementation("junit:junit:4.13.2")
}

// Fails the build if any core source uses desktop-only APIs that Android lacks
// (imports or fully-qualified names; comment lines are ignored).
val checkHeadless by tasks.registering {
    val files = sourceSets.main.get().java
    inputs.files(files)
    doLast {
        val banned = Regex("""\b(java\.awt|javax\.swing|javax\.imageio|java\.beans)\.""")
        val comment = Regex("""^\s*(//|\*|/\*)""")
        val offenders = files.files.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                if (!comment.containsMatchIn(line) && banned.containsMatchIn(line)) {
                    "${f.relativeTo(rootDir)}:${i + 1}: ${line.trim()}"
                } else {
                    null
                }
            }
        }
        if (offenders.isNotEmpty()) {
            error("Desktop-only APIs used in :core sources:\n" + offenders.joinToString("\n"))
        }
    }
}
tasks.named("check") { dependsOn(checkHeadless) }
tasks.named("compileJava") { dependsOn(checkHeadless) }
