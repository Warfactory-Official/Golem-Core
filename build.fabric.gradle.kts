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
    exclusiveContent {
        forRepository { maven("https://maven.caffeinemc.net/releases") { name = "CaffeineMC" } }
        filter { includeGroup("net.caffeinemc") }
    }
    exclusiveContent {
        forRepository { maven("https://maven.ryanhcode.dev/releases") { name = "RyanHCode" } }
        filter {
            includeGroup("dev.ryanhcode.sable")
            includeGroup("dev.ryanhcode.sable-companion")
        }
    }
    // KubeJS and Rhino (the optional `kubejs` peer, see deps.kubejs).
    exclusiveContent {
        forRepository { maven("https://maven.latvian.dev/releases") { name = "LatvianDev" } }
        filter { includeGroup("dev.latvian.mods") }
    }
    // Jade (the optional `jade` peer, see deps.jade), published on Modrinth's maven.
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
    // REI and its Cloth Config / Architectury dependencies (the optional `rei` peer, see deps.rei).
    exclusiveContent {
        forRepository { maven("https://maven.shedaniel.me") { name = "Shedaniel" } }
        filter {
            includeGroup("me.shedaniel")
            includeGroup("me.shedaniel.cloth")
            includeGroup("dev.architectury")
        }
    }
}

// Sable (moving block sub-levels), an optional peer on the nodes that declare it: the shared integration in
// src/sable plus this loader's entry points in src/sable-fabric, added only here and listed in fabric.mod.json only
// here (appended to its entrypoints and mixins by processResources). Compiled against, never shipped or required.
val sableMc = (findProperty("deps.sable_mc") as String?)?.takeIf { it.isNotBlank() }
val sableVer = (findProperty("deps.sable") as String?)?.takeIf { it.isNotBlank() }
val sableCompanionVer = (findProperty("deps.sable_companion") as String?)?.takeIf { it.isNotBlank() }
val hasSable = sableMc != null && sableVer != null && sableCompanionVer != null
if (hasSable) {
    sourceSets["main"].java.srcDir(rootProject.file("src/sable/java"))
    sourceSets["main"].java.srcDir(rootProject.file("src/sable-fabric/java"))
    sourceSets["main"].resources.srcDir(rootProject.file("src/sable/resources"))
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
    if (hasSable && obfuscated) {
        // Sable's fabric jar carries its common classes; Companion and Sodium's API are their own small jars.
        "modCompileOnly"("dev.ryanhcode.sable:sable-fabric-$sableMc:$sableVer") { isTransitive = false }
        "modCompileOnly"("dev.ryanhcode.sable-companion:sable-companion-fabric-$sableMc:$sableCompanionVer") { isTransitive = false }
        (findProperty("deps.sodium_api") as String?)?.takeIf { it.isNotBlank() }?.let {
            "modCompileOnly"("net.caffeinemc:sodium-fabric-api:$it") { isTransitive = false }
        }
    }
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
        // The 1.20.5 physics attributes, backported on 1.20.1 only (dev.golem.core.world.Physics).
        "physics_mixins" to if (mcVersion.startsWith("1.20.")) ", \"MixinLegacyAttributeSupplier\", \"MixinLegacyLivingPhysics\", \"MixinLegacyFabricGravity\"" else "",
        // dev.golem.core.gametest.TestChunks: a 26.1 test waits until its whole plot is entity-ticking.
        "gametest_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinGameTestChunks\"" else "",
        "rail_mixins" to ", \"MixinMinecartRails\"",
        // Gliders: fabric-api's EntityElytraEvents.CUSTOM grants the flight; only the wings mixins (always listed) apply.
        "glider_mixins" to "",
        "glider_client_mixins" to "",
        // SlotSync: from 26.1 the client echoes server slot pushes from the creative screen.
        "creative_echo_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinCreativeEchoPush\", \"MixinCreativeEchoListener\"" else "",
        "world_effect_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinWorldEffects\"" else "",
        // Skies.Modifier: 26.1 appends environment-attribute layers; older nodes wrap the sky darkening and the
        // client colour getters (the mixin classes are empty stubs on the other era).
        "sky_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinEnvironmentSkyLayers\"" else ", \"MixinLevelSkyDarken\"",
        "sky_client_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) "" else
            ", \"MixinClientLevelSky\", \"MixinDimensionEffectsSky\", \"MixinOverworldFogColor\"",
        // Clouds: Fabric 26.1 has no cloud hook (DimensionRenderingRegistry serves 1.20.1/1.21.1).
        "cloud_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinCloudRender\", \"MixinWeatherRender\"" else "",
        // Fabric is told about client block entities by fabric-api, so it needs no mixin for it.
        "flywheel_be_mixins" to "",
        // Fabric has no debug-screen entry registry, so the F3 lines are appended by a mixin there.
        "debug_line_mixins" to if (hasVendoredFlywheel) ", \"MixinDebugScreenLines\"" else "",
        // 1.20.1 has no pre-removal explosion hook for blocks (1.21 added onExplosionHit); see CompatBlock.
        // MixinLegacyStackLimit: no per-stack stack size before the 1.20.5 component; see ItemStacks.setMaxStackSize.
        "legacy_explosion_mixins" to if (mcVersion.startsWith("1.20.")) ", \"MixinExplosionBlockHook\", \"MixinLegacyStackLimit\"" else "",
        // MixinFabricGameTestModInitializer is not listed here: Fabric's GameTest module exists only in dev runs, so
        // dev.golem.core.gametest.GameTestMixinPlugin adds it when its target is present (a listed mixin with a missing
        // target fails the required config and crashed players' clients).
        "legacy_client_mixins" to ", \"MixinMouseInput\"" +
            (if (mcVersion.substringBefore('.').toInt() < 26) ", \"MixinLegacyKeyMappingWinner\"" else "") +
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
    // Only Forge generates a refmap; naming an absent one leaves the jar pointing at a file it lacks.
    filesMatching("*.mixins.json") { filter { if (it.trimStart().startsWith("\"refmap\"")) null else it } }
    filesMatching("fabric.mod.json") {
        filter { line -> if (line.trim() == "{")
            "{\n  \"accessWidener\": \"$accessWidenerName\"," else line }
    }
    // The Sable integration's entry points and mixin config, only on nodes that declare Sable. Appended by line rather
    // than templated, so the source fabric.mod.json stays valid JSON (Loom parses it at configuration).
    if (hasSable) {
        val sableLines = mapOf(
            "\"dev.golem.core.platform.FabricEntrypoint\"" to "\"dev.golem.core.sable.SableFabric\"",
            "\"dev.golem.core.harness.HarnessFabricClient\"" to "\"dev.golem.core.sable.SableFabricClient\"",
            "\"golemcore.harness.mixins.json\"" to "\"golemcore.sable.mixins.json\"",
        )
        filesMatching("fabric.mod.json") {
            filter { line -> sableLines[line.trim()]?.let { "$line, $it" } ?: line }
        }
    }
    // Jade's plugin entrypoint, only where the adapter is compiled. Appended by line, like Sable's.
    if (!(findProperty("deps.jade") as String?).isNullOrBlank()) {
        filesMatching("fabric.mod.json") {
            filter { line -> if (line.trim() == "\"entrypoints\": {")
                "$line\n        \"jade\": [\"dev.golem.core.jade.JadePlugin\"]," else line }
        }
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
    // Golem Core's KubeJS plugin list names classes that exist only where the `kubejs` constant holds.
    if ((findProperty("deps.kubejs") as String?).isNullOrBlank()) exclude("kubejs.plugins.txt")
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

    // REI, compile-only and per-node optional, like JEI: the adapter in dev.golem.core.rei feeds a mod's neutral
    // viewer declarations (dev.golem.core.viewer) to REI 12/16/26. Fabric REI jars are intermediary-mapped below 26.1.
    // Its POM's Fabric Loader / Fabric API are left out: the node declares its own.
    (findProperty("deps.rei") as String?)?.takeIf { it.isNotBlank() }?.let { rei ->
        (if (obfuscated) "modCompileOnly" else "compileOnly")("me.shedaniel:RoughlyEnoughItems-api-fabric:$rei") {
            exclude(group = "net.fabricmc")
            exclude(group = "net.fabricmc.fabric-api")
        }
        // REI 26's Fabric POM lists Cloth Config and Architectury at runtime scope; REI's API types name theirs
        // (Point, Rectangle, FluidStack), so the compile classpath needs them as well.
        for (dep in listOfNotNull(
            (findProperty("deps.rei_cloth") as String?)?.takeIf { it.isNotBlank() }?.let { "me.shedaniel.cloth:cloth-config-fabric:$it" },
            (findProperty("deps.rei_architectury") as String?)?.takeIf { it.isNotBlank() }?.let { "dev.architectury:architectury-fabric:$it" },
        )) (if (obfuscated) "modCompileOnly" else "compileOnly")(dep) {
            exclude(group = "net.fabricmc")
            exclude(group = "net.fabricmc.fabric-api")
        }
    }

    // Jade, compile-only and per-node optional (see the NeoForge buildscript); intermediary-mapped below 26.1.
    (findProperty("deps.jade") as String?)?.takeIf { it.isNotBlank() }?.let {
        (if (obfuscated) "modCompileOnly" else "compileOnly")("maven.modrinth:jade:$it") { isTransitive = false }
    }

    // KubeJS 6, compile-only and per-node optional (see the NeoForge buildscript); intermediary-mapped below 26.1.
    // KubeJS 6's API names Rhino's and Architectury's types; the node declares its own Fabric Loader / Fabric API.
    (findProperty("deps.kubejs") as String?)?.takeIf { it.isNotBlank() }?.let { kubejs ->
        for (dep in listOfNotNull(
            "dev.latvian.mods:kubejs-fabric:$kubejs",
            "dev.latvian.mods:rhino-fabric:${prop("deps.kubejs_rhino")}",
            (findProperty("deps.kubejs_architectury") as String?)?.takeIf { it.isNotBlank() }?.let { "dev.architectury:architectury-fabric:$it" },
        )) (if (obfuscated) "modCompileOnly" else "compileOnly")(dep) { isTransitive = false }
    }

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

// The uploaded copy of this node's jar, without the test harness.
apply(from = rootProject.file("gradle/release-jar.gradle.kts"))

// Modrinth + CurseForge upload of this node's jar; run every node with the root `publishAllMods`.
apply(from = rootProject.file("gradle/mod/publish.gradle.kts"))

tasks.jar {
    from(files(rootProject.file("LICENSE"), rootProject.file("LICENSE.LESSER"))) { rename { "${it}_${prop("mod.id")}" } }
}
