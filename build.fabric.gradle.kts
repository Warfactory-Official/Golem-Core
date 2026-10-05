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
    maven("https://maven.squiddev.cc") {
        name = "SquidDev"
        content { includeGroup("cc.tweaked") }
    }
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
// Loom validates every widened member against the node's jar, so 1.21.1 (which has VehicleEntity to widen)
// and 1.20.1 (which does not) need separate legacy files.
val accessWidenerName = if (!obfuscated) "golemcore.accesswidener"
    else if (mcVersion.startsWith("1.21")) "golemcore.legacy-1.21.accesswidener"
    else "golemcore.legacy.accesswidener"
val allAccessWideners = listOf("golemcore.accesswidener", "golemcore.legacy.accesswidener", "golemcore.legacy-1.21.accesswidener")

// The vendored instancing backend names the blaze3d GPU-buffer API introduced with 26.1. The two
// GlBuffer fields it reads are widened here by golemcore.accesswidener, which is also the file this
// node already uses — so unlike NeoForge, where the widenings are a second access transformer that
// has to be merged in, nothing else is needed to carry them.
val hasVendoredFlywheel = !obfuscated

dependencies {
    "minecraft"("com.mojang:minecraft:${prop("deps.minecraft")}")
    val cfg = if (obfuscated) "modImplementation" else "implementation"
    if (obfuscated) "mappings"(loom.officialMojangMappings())
    cfg("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    cfg("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
    // Team Reborn Energy: an optional peer for EnergyCompat/EnergyExposure (RebornEnergy), compiled against,
    // never shipped or required. Published on the Fabric maven Loom already adds.
    if (project.hasProperty("deps.tr_energy")) {
        (if (obfuscated) "modCompileOnly" else "compileOnly")("teamreborn:energy:${prop("deps.tr_energy")}") {
            isTransitive = false
        }
    }
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
        "legacy_item_owner_mixins" to if (mcVersion.substringBefore('.').toInt() < 26)
            ", \"MixinItemRenderer\"" else "",
        // Fabric API has no minecart events, so every Fabric node calls the rail hooks through a mixin.
        "rail_mixins" to ", \"MixinMinecartRails\"",
        "world_effect_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinWorldEffects\"" else "",
        // Skies.Modifier: 26.1 appends environment-attribute layers; older nodes wrap the sky darkening and the
        // client colour getters (the mixin classes are empty stubs on the other era).
        "sky_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinEnvironmentSkyLayers\"" else ", \"MixinLevelSkyDarken\"",
        "sky_client_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) "" else
            ", \"MixinClientLevelSky\", \"MixinDimensionEffectsSky\", \"MixinOverworldFogColor\"",
        // Fabric is told about client block entities by fabric-api, so it needs no mixin for it.
        "flywheel_be_mixins" to "",
        // Fabric has no debug-screen entry registry, so the F3 lines are appended by a mixin there.
        "debug_line_mixins" to if (hasVendoredFlywheel) ", \"MixinDebugScreenLines\"" else "",
        // 1.20.1 has no pre-removal explosion hook for blocks (1.21 added onExplosionHit); see CompatBlock.
        // MixinLegacyStackLimit: no per-stack stack size before the 1.20.5 component; see ItemStacks.setMaxStackSize.
        "legacy_explosion_mixins" to if (mcVersion.startsWith("1.20.")) ", \"MixinExplosionBlockHook\", \"MixinLegacyStackLimit\"" else "",
        // Modern Fabric's GameTest API has no public dynamic-instance registration callback. The
        // exact mixin is compiled and named only where Fabric's 26.1 implementation provides it.
        // Both entries follow MixinLivingEntitySwing, so each carries its own leading comma.
        "fabric_gametest_mixins" to if (!obfuscated) ", \"MixinFabricGameTestModInitializer\"" else "",
        "legacy_client_mixins" to ", \"MixinMouseInput\"" +
            (if (obfuscated) ", \"MixinLegacyCrosshair\"" else "") +
            // VanillaHud: Fabric API before 26.1 has no named HUD elements to suppress hearts/armour by.
            (if (obfuscated) ", \"MixinLegacyHudRows\"" else ""),
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
    allAccessWideners.filter { it != accessWidenerName }.forEach { exclude(it) }
    exclude(
        "META-INF/accesstransformer.cfg",
        "META-INF/golemcore.neoforge.accesstransformer.cfg",
        "META-INF/golemcore.forge.accesstransformer.cfg",
        // NeoForge's spelling of the same two GlBuffer widenings this node gets from its access
        // widener. Never both.
        "META-INF/golemcore.flywheel.accesstransformer.cfg",
    )
    // The golem_* shaders belong to the vendored backend, which exists only from 26.1. Keep
    // unrelated Core shaders such as depth_merge.
    if (!hasVendoredFlywheel) {
        exclude("assets/golemcore/shaders/core/golem_*")
    }
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

    // CC: Tweaked, compile-only and per-node optional: dev.golem.core.computer registers peripherals with the
    // loader's lookup (NeoForge capability, Forge provider, Fabric BlockApiLookup). The peripheral API itself
    // (core-api) is the same on every node. A node without coordinates compiles the facade out.
    val ccMc = (findProperty("deps.cc_mc") as String?)?.takeIf { it.isNotBlank() }
    val ccVer = (findProperty("deps.cc") as String?)?.takeIf { it.isNotBlank() }
    if (ccMc != null && ccVer != null) {
        // core-api names no Minecraft type, so it needs no remapping; FabricPeripherals reaches CC's lookup by its
        // id rather than through the fabric-api jar, which Loom 1.16 refuses to remap on 1.20.1/1.21.1 (that jar
        // was built with Loom 1.17).
        compileOnly("cc.tweaked:cc-tweaked-$ccMc-core-api:$ccVer") { isTransitive = false }
    }
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
