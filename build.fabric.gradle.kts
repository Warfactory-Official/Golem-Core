plugins {
    id("java-library")
    id("maven-publish")
    id("dev.kikugie.loom-back-compat")
}

fun prop(name: String): String = property(name) as String

val mcVersion = stonecutter.current.version
val javaVersion = prop("java_version").toInt()

group = prop("mod.group")
version = "${prop("mod.version")}+$mcVersion"
base.archivesName = "${prop("mod.id")}-fabric-$mcVersion"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://maven.blamejared.com") { name = "BlameJared" }
}

// Loom's configurations are referenced by name rather than through generated accessors: Kotlin DSL
// does not emit type-safe accessors for a build script that Stonecutter assigns under a non-default
// file name (build.fabric.gradle.kts).
//
// Which configuration to use depends on whether the target is obfuscated:
//   - 26.1+ ships unobfuscated, so Loom runs with remapping off and never creates the `mod*`
//     configurations at all. Plain `implementation` is correct, and no `mappings` layer is needed.
//   - <=1.21.11 is obfuscated, so dependencies must go through `modImplementation` to be remapped,
//     and Mojmap must be layered in explicitly.
// Mojmap is the single name namespace across every target, which is why obfuscation never becomes a
// porting problem here — only a build-wiring one.
// Note this is an ordinary Kotlin conditional, not a Stonecutter `//?` comment: Stonecutter only
// preprocesses files under src/, never build scripts.
val obfuscated = mcVersion.substringBefore('.').toInt() < 26
val accessWidenerName = if (obfuscated) "golemcore.legacy.accesswidener" else "golemcore.accesswidener"

dependencies {
    "minecraft"("com.mojang:minecraft:${prop("deps.minecraft")}")
    val cfg = if (obfuscated) "modImplementation" else "implementation"
    if (obfuscated) "mappings"(loom.officialMojangMappings())
    cfg("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    cfg("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
}

loom {
    accessWidenerPath = file("../../src/main/resources/$accessWidenerName")
    runs {
        named("client") {
            client()
            configName = "Fabric Client ($mcVersion)"
            ideConfigGenerated(true)
            runDir = "runs/client"
        }
        named("server") {
            server()
            configName = "Fabric Server ($mcVersion)"
            ideConfigGenerated(true)
            runDir = "runs/server"
        }
    }
}

tasks.processResources {
    val props = mapOf(
        "mod_id" to prop("mod.id"),
        "mod_name" to prop("mod.name"),
        "mod_version" to prop("mod.version"),
        "mod_author" to prop("mod.author"),
        "mod_license" to prop("mod.license"),
        "mod_description" to prop("mod.description").replace("\n", "\\n"),
        "minecraft_range" to prop("deps.minecraft_range"),
        "fabric_loader" to prop("deps.fabric_loader"),
        "java_version" to javaVersion.toString(),
        "mixin_compatibility" to if (javaVersion <= 17) "JAVA_17" else "JAVA_21",
        "world_effect_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinWorldEffects\"" else "",
        "legacy_client_mixins" to ", \"MixinMouseInput\"" +
            if (obfuscated) ", \"MixinLegacyCrosshair\"" else "",
        "pack_format" to prop("pack_format"),
        "pack_format_entry" to if (mcVersion.substringBefore('.').toInt() >= 26)
            "\"min_format\": ${prop("pack_format")}, \"max_format\": ${prop("pack_format")}" 
        else
            "\"pack_format\": ${prop("pack_format")}",
    )
    inputs.properties(props)
    filesMatching(listOf("fabric.mod.json", "pack.mcmeta", "*.mixins.json")) { expand(props) }
    filesMatching("fabric.mod.json") {
        filter { line -> if (line.trim() == "{")
            "{\n  \"accessWidener\": \"$accessWidenerName\"," else line }
    }
    if (obfuscated) exclude("golemcore.accesswidener") else exclude("golemcore.legacy.accesswidener")
    exclude(
        "META-INF/accesstransformer.cfg",
        "META-INF/golemcore.neoforge.accesstransformer.cfg",
        "META-INF/golemcore.forge.accesstransformer.cfg",
        // The flywheel widenings, the interface injection and the shaders they exist for all belong
        // to the NeoForge-only vendored Flywheel backend, which compiles out entirely on this loader.
        "META-INF/golemcore.flywheel.accesstransformer.cfg",
        "META-INF/interface_injection.json", "assets/golemcore/shaders/**",
    )
    exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }
}

// Stonecutter rewrites the shared src/ tree in place for the active node. Both compilation and
// resource processing must wait for that rewrite, or they consume the previous node's source state.
tasks.compileJava { dependsOn(tasks.named("stonecutterGenerate")) }
tasks.processResources { dependsOn(tasks.named("stonecutterGenerate")) }

dependencies {
    // Nullness annotations. NeoForge ships jspecify transitively on 26.1 but not on older versions,
    // so declare it explicitly: it is an annotations-only jar with no runtime component, safe on every
    // target, and keeps @Nullable usable in shared code across the whole matrix.
    compileOnly("org.jspecify:jspecify:1.0.0")

    // JEI, compile-only and per-node optional: the facade in dev.golem.core.jei exists to hide the
    // 19.x/29.x API split from consumers, so this library has to see whichever line the node targets.
    // A node that declares no coordinates compiles the facade out entirely (the `jei` constant).
    val jeiMc = (findProperty("deps.jei_mc") as String?)?.takeIf { it.isNotBlank() }
    val jeiVer = (findProperty("deps.jei") as String?)?.takeIf { it.isNotBlank() }
    if (jeiMc != null && jeiVer != null) compileOnly("mezz.jei:jei-$jeiMc-common-api:$jeiVer")
}

// Plain-JVM unit tests. The data backends are pure logic over CompoundTag/NbtOps and need no game
// bootstrap, which makes them the one part of the compat layer that is cheaply testable off-thread.
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.named("stonecutterGenerateTest"))
    testLogging { events("passed", "failed", "skipped") }
}
