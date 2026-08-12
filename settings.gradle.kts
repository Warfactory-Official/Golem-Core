pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.7"
}

stonecutter {
    create(rootProject) {
        // One node per (minecraft version, loader) pair. The node name carries the loader suffix, so
        // the logical version used by `//? if >=1.21.1` conditions must be given separately — otherwise
        // Stonecutter parses "-neoforge" as a semver pre-release tag and orders it BELOW the release.
        fun match(version: String, vararg loaders: String) = loaders.forEach {
            version("$version-$it", version).buildscript = "build.$it.gradle.kts"
        }

        match("26.1", "fabric", "neoforge")
        // 1.21.1 fabric is deliberately absent for now: Loom 1.15.5 targets the unobfuscated era, and
        // wiring the obfuscated path needs `dev.kikugie.loom-back-compat`. NeoForge alone is enough to
        // prove the version-axis backports compile, which is what this node exists for.
        match("1.21.1", "neoforge")
        // Phase 2: match("1.20.1", "fabric", "forge")

        vcsVersion = "26.1-neoforge"
    }
}

rootProject.name = "Golem-Core"
