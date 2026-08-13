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
    // Picks the right Fabric Loom variant per node. Loom split at the obfuscation boundary:
    // `net.fabricmc.fabric-loom` builds the unobfuscated era (26.1+) and its officialMojangMappings()
    // throws outright, while `net.fabricmc.fabric-loom-remap` carries the remapping pipeline for
    // everything below. Both ship under the same version line, so this selects the plugin id rather
    // than juggling two Loom versions on one buildscript classpath. Its default selector is exactly
    // ours — obfuscated below 26 — so no explicit `loomx.obfuscated` is needed.
    id("dev.kikugie.loom-back-compat") version "0.4.2"
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
        match("1.21.1", "fabric", "neoforge")
        // Phase 2: match("1.20.1", "fabric", "forge")

        vcsVersion = "26.1-neoforge"
    }
}

rootProject.name = "Golem-Core"
