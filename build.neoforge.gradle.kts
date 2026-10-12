plugins {
    id("java-library")
    id("maven-publish")
    id("net.neoforged.moddev") version "2.0.141"
}

fun prop(name: String): String = property(name) as String

val mcVersion = stonecutter.current.version
val javaVersion = prop("java_version").toInt()

// The vendored Flywheel backend in dev.golem.core.flywheel only exists from 26.1 — it is written
// against the blaze3d GPU-buffer/pipeline API that arrived with it. Both its access transformer and
// its access transformer names types that do not exist before then, so declaring it on an older
// node fails the build outright rather than being ignored.
val hasVendoredFlywheel = mcVersion.substringBefore('.').toInt() >= 26

// Sable (see the dependency block below) on the nodes that declare its coordinates.
val sableMc = (findProperty("deps.sable_mc") as String?)?.takeIf { it.isNotBlank() }
val sableVer = (findProperty("deps.sable") as String?)?.takeIf { it.isNotBlank() }
val sableCompanionVer = (findProperty("deps.sable_companion") as String?)?.takeIf { it.isNotBlank() }
val hasSable = sableMc != null && sableVer != null && sableCompanionVer != null

// NeoForge reads exactly one META-INF/accesstransformer.cfg per jar, so the backend's widenings
// cannot ship as a second file — and they cannot join the shared one either, because GlBuffer does
// not exist before 26.1 and an access transformer naming a missing class fails the node outright.
// The shipped file is therefore assembled from whichever sources apply to this node.
val accessTransformerFiles = listOfNotNull(
    file("../../src/main/resources/META-INF/golemcore.neoforge.accesstransformer.cfg")
        .takeIf { it.exists() },
    file("../../src/main/resources/META-INF/golemcore.flywheel.accesstransformer.cfg")
        .takeIf { hasVendoredFlywheel && it.exists() },
)
val mergedAccessTransformer = layout.buildDirectory.file("golemcore-at/accesstransformer.cfg")
val mergeAccessTransformers = tasks.register("mergeAccessTransformers") {
    inputs.files(accessTransformerFiles)
    outputs.file(mergedAccessTransformer)
    doLast {
        val out = mergedAccessTransformer.get().asFile
        out.parentFile.mkdirs()
        out.writeText(accessTransformerFiles.joinToString("\n") { it.readText().trim() } + "\n")
    }
}

group = prop("mod.group")
version = "${prop("mod.version")}+$mcVersion"
base.archivesName = "${prop("mod.id")}-neoforge-$mcVersion"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

neoForge {
    version = prop("deps.neoforge")

    // Node projects live in versions/<node>/ and have no src/ of their own — the shared tree is two
    // levels up. A bare "src/main/..." here resolves to nothing, and because the declaration is
    // guarded on exists() that failure is silent: the transformer is simply never applied.
    for (at in accessTransformerFiles) accessTransformers.from(at)

    runs {
        configureEach {
            ideName = "NeoForge ${name.replaceFirstChar { it.uppercase() }} ($mcVersion)"
        }
        register("client") {
            client()
            gameDirectory = file("runs/client")
        }
        register("server") {
            server()
            gameDirectory = file("runs/server")
        }
    }

    mods {
        register(prop("mod.id")) { sourceSet(sourceSets["main"]) }
    }

    // Unit tests compile against Minecraft too — the data backends are built on CompoundTag/NbtOps.
    // MDG only wires the modding dependencies into `main` by default.
    addModdingDependenciesTo(sourceSets["test"])

    // And some of them have to *run* under FML. NeoForge's SharedConstants asks FMLEnvironment
    // whether it is in production during its static init, which throws "There is no current FML
    // Loader" in a plain test JVM — so any test that touches Bootstrap or a vanilla registry dies
    // in @BeforeAll rather than failing an assertion. This launches the test JVM through FML's own
    // JUnit entry point, which is the only thing that makes registry-backed tests possible here.
    unitTest {
        enable()
        testedMod = mods.named(prop("mod.id"))
    }
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

// Sable (moving block sub-levels; Create Aeronautics), an optional peer on the nodes that declare it. Its backend of
// dev.golem.core.world.SubLevels lives in its own source roots, src/sable (shared) and src/sable-neoforge (this
// loader's entry points), added only here: no other node sees them,
// and the shared tree carries no guard for it. Installed at setup only when the `sable` mod is loaded.
if (hasSable) {
    sourceSets["main"].java.srcDir(rootProject.file("src/sable/java"))
    sourceSets["main"].java.srcDir(rootProject.file("src/sable-neoforge/java"))
    sourceSets["main"].resources.srcDir(rootProject.file("src/sable/resources"))
    dependencies {
        compileOnly("dev.ryanhcode.sable-companion:sable-companion-common-$sableMc:$sableCompanionVer") { isTransitive = false }
        // The split listener (keeping multiblocks whole) is Sable's own API, not Companion's.
        compileOnly("dev.ryanhcode.sable:sable-common-$sableMc:$sableVer") { isTransitive = false }
        // Sodium's public API: Sable's sub-level meshes never mark animated sprites drawn, so the Sable root does
        // (SableSpriteActivity, loaded only when Sodium is present).
        (findProperty("deps.sodium_api") as String?)?.takeIf { it.isNotBlank() }?.let {
            compileOnly("net.caffeinemc:sodium-neoforge-api:$it") { isTransitive = false }
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
        "mod_description" to prop("mod.description"),
        "minecraft_range" to prop("deps.minecraft_range"),
        "neoforge_version" to prop("deps.neoforge"),
        "neoforge_loader_range" to prop("deps.neoforge_loader_range"),
        "java_version" to javaVersion.toString(),
        "mixin_compatibility" to if (javaVersion <= 17) "JAVA_17" else "JAVA_21",
        "legacy_item_owner_mixins" to if (mcVersion.substringBefore('.').toInt() < 26)
            ", \"MixinItemRenderer\"" else "",
        // NeoForge 1.21.1 still calls getRailMaxSpeed/onMinecartPass; 26.1 declares but no longer calls them.
        // The 1.20.5 physics attributes, backported on 1.20.1 only (dev.golem.core.world.Physics).
        "physics_mixins" to "",
        // dev.golem.core.gametest.TestChunks: a 26.1 test waits until its whole plot is entity-ticking.
        "gametest_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinGameTestChunks\"" else "",
        "rail_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinMinecartRails\"" else "",
        // Gliders: 26.1 has canGlide (covers the starts too); 1.21.1 asks the chest item at three points.
        "glider_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinGliderFlight\"" else ", \"MixinGliderFlight\", \"MixinGliderStart\"",
        "glider_client_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) "" else ", \"MixinGliderClientStart\"",
        "world_effect_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinWorldEffects\"" else "",
        // Skies.Modifier: 26.1 appends environment-attribute layers; older nodes wrap the sky darkening and the
        // client colour getters (the mixin classes are empty stubs on the other era).
        "sky_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinEnvironmentSkyLayers\"" else ", \"MixinLevelSkyDarken\"",
        "sky_client_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) "" else
            ", \"MixinClientLevelSky\", \"MixinDimensionEffectsSky\", \"MixinOverworldFogColor\"",
        // Clouds: NeoForge 26.1 uses CustomCloudsRenderer; 1.21.1 has only the effects object's renderClouds.
        "cloud_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) "" else ", \"MixinLegacyCloudRender\", \"MixinLegacyWeatherRender\"",
        "flywheel_be_mixins" to if (hasVendoredFlywheel) ", \"MixinLevelChunkVisuals\"" else "",
        // The Sable integration's own mixin config (src/sable/resources), only on nodes that declare Sable.
        "sable_mixins" to if (hasSable) "[[mixins]]\nconfig = \"golemcore.sable.mixins.json\"" else "",
        "pack_format" to prop("pack_format"),
        "pack_format_entry" to if (mcVersion.substringBefore('.').toInt() >= 26)
            "\"min_format\": ${prop("pack_format")}, \"max_format\": ${prop("pack_format")}" 
        else
            "\"pack_format\": ${prop("pack_format")}",
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta", "*.mixins.json")) { expand(props) }
    exclude(
        "fabric.mod.json", "golemcore.fabric.mixins.json",
        "META-INF/accesstransformer.cfg", "META-INF/golemcore.neoforge.accesstransformer.cfg",
        "META-INF/golemcore.forge.accesstransformer.cfg",
        "META-INF/golemcore.flywheel.accesstransformer.cfg",
    )
    dependsOn(mergeAccessTransformers)
    from(mergedAccessTransformer) { into("META-INF") }
    // The golem_* shaders belong to the vendored Flywheel backend, which only exists on 26.1.
    // Keep unrelated Core shaders such as depth_merge.
    if (!hasVendoredFlywheel) {
        exclude("assets/golemcore/shaders/core/golem_*")
    }
}

// Stonecutter writes this project's sources from the root src/ tree; the toolkit must not read them
// before that has happened.
tasks.named("createMinecraftArtifacts") { dependsOn(tasks.named("stonecutterGenerate")) }

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
    compileOnly("org.spongepowered:mixin:0.8.5")
    // MixinExtras (@WrapOperation, @ModifyReturnValue): NeoForge bundles it at runtime on every line we target, so
    // compile against the oldest API we use and ship nothing.
    compileOnly("io.github.llamalad7:mixinextras-common:0.3.5")

    // JEI, compile-only and per-node optional: the facade in dev.golem.core.jei exists to hide the
    // 19.x/29.x API split from consumers, so this library has to see whichever line the node targets.
    // A node that declares no coordinates compiles the facade out entirely (the `jei` constant).
    val jeiMc = (findProperty("deps.jei_mc") as String?)?.takeIf { it.isNotBlank() }
    val jeiVer = (findProperty("deps.jei") as String?)?.takeIf { it.isNotBlank() }
    if (jeiMc != null && jeiVer != null) compileOnly("mezz.jei:jei-$jeiMc-common-api:$jeiVer")

    // REI, compile-only and per-node optional, like JEI: the adapter in dev.golem.core.rei feeds a mod's neutral
    // viewer declarations (dev.golem.core.viewer) to REI 16/26. A node without deps.rei compiles it out (`rei`).
    (findProperty("deps.rei") as String?)?.takeIf { it.isNotBlank() }?.let { rei ->
        compileOnly("me.shedaniel:RoughlyEnoughItems-api-neoforge:$rei")
    }

    // CC: Tweaked, compile-only and per-node optional: dev.golem.core.computer registers peripherals with the
    // loader's lookup (NeoForge capability, Forge provider, Fabric BlockApiLookup). The peripheral API itself
    // (core-api) is the same on every node. A node without coordinates compiles the facade out.
    val ccMc = (findProperty("deps.cc_mc") as String?)?.takeIf { it.isNotBlank() }
    val ccVer = (findProperty("deps.cc") as String?)?.takeIf { it.isNotBlank() }
    if (ccMc != null && ccVer != null) {
        compileOnly("cc.tweaked:cc-tweaked-$ccMc-core-api:$ccVer") { isTransitive = false }
        compileOnly("cc.tweaked:cc-tweaked-$ccMc-forge-api:$ccVer") { isTransitive = false }
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

// Modrinth + CurseForge upload of this node's jar; run every node with the root `publishAllMods`.
apply(from = rootProject.file("gradle/mod/publish.gradle.kts"))
