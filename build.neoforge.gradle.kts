plugins {
    id("java-library")
    id("maven-publish")
    id("net.neoforged.moddev") version "2.0.141"
}

fun prop(name: String): String = property(name) as String
fun propOrNull(name: String): String? = (findProperty(name) as String?)?.takeIf { it.isNotBlank() }

val mcVersion = stonecutter.current.version
apply(from = rootProject.file("gradle/mod/gemrender-local.gradle.kts"))
val localGemRender = extra.properties["golem.gemrender.local"]
val javaVersion = prop("java_version").toInt()

// Sable (see the dependency block below) on the nodes that declare its coordinates.
val sableMc = (findProperty("deps.sable_mc") as String?)?.takeIf { it.isNotBlank() }
val sableVer = (findProperty("deps.sable") as String?)?.takeIf { it.isNotBlank() }
val sableCompanionVer = (findProperty("deps.sable_companion") as String?)?.takeIf { it.isNotBlank() }
val hasSable = sableMc != null && sableVer != null && sableCompanionVer != null

// NeoForge reads exactly one META-INF/accesstransformer.cfg per jar; it is assembled from whichever sources
// apply to this node.
val accessTransformerFiles = listOfNotNull(
    file("../../src/main/resources/META-INF/golemcore.neoforge.accesstransformer.cfg")
        .takeIf { it.exists() },
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
base.archivesName = "${prop("mod.id")}-neoforge"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

// The NeoForge this build runs on: deps.neoforge, or deps.neoforge_kubejs in a -Pkubejs.run=true dev run (KubeJS may need
// an older build; see versions/*/gradle.properties). Release jars always use deps.neoforge.
val neoforgeVersion = propOrNull("deps.neoforge_kubejs")?.takeIf { propOrNull("kubejs.run") == "true" } ?: prop("deps.neoforge")
// The oldest NeoForge the jar accepts (neoforge.mods.toml): deps.neoforge_floor where a node sets one, else the build's.
val neoforgeFloor = propOrNull("deps.neoforge_floor") ?: neoforgeVersion

neoForge {
    version = neoforgeVersion

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
    // GemRender's CurseForge files (deps.gemrender) and Flywheel's API (deps.gemrender_flywheel).
    exclusiveContent {
        forRepository { maven("https://cursemaven.com") { name = "CurseMaven" } }
        filter { includeGroup("curse.maven") }
    }
    exclusiveContent {
        forRepository { maven("https://maven.createmod.net") { name = "CreateMod" } }
        filter { includeGroup("dev.engine-room.flywheel") }
    }
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

// Mo' Bends (bendy player animations), an optional client peer on the nodes that declare it. Its backend of
// dev.golem.core.render.BodyAnimations lives in its own source roots, src/mobends and src/mobends-neoforge, added only
// here. Installed at client setup only when the `mobends` mod is loaded.
if (propOrNull("deps.mobends") != null) {
    sourceSets["main"].java.srcDir(rootProject.file("src/mobends/java"))
    sourceSets["main"].java.srcDir(rootProject.file("src/mobends-neoforge/java"))
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
        "neoforge_version" to neoforgeFloor,
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
        // SlotSync: from 26.1 the client echoes server slot pushes from the creative screen.
        "creative_echo_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinCreativeEchoPush\", \"MixinCreativeEchoListener\"" else "",
        "world_effect_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinWorldEffects\"" else "",
        // Skies.Modifier: 26.1 appends environment-attribute layers; older nodes wrap the sky darkening and the
        // client colour getters (the mixin classes are empty stubs on the other era).
        "sky_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinEnvironmentSkyLayers\"" else ", \"MixinLevelSkyDarken\"",
        "sky_client_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) "" else
            ", \"MixinClientLevelSky\", \"MixinDimensionEffectsSky\", \"MixinOverworldFogColor\"",
        // Clouds: NeoForge 26.1 uses CustomCloudsRenderer; 1.21.1 has only the effects object's renderClouds.
        "cloud_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) "" else ", \"MixinLegacyCloudRender\", \"MixinLegacyWeatherRender\"",
        // GemRender 0.1.14 patches (26.1 only, applied by version; see GemRenderMixinPlugin)
        "gemrender_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) "\"GameRendererBob\", \"MixinRenderContextBob\"" else "",
        "gemrender_mixin_plugin" to "dev.golem.core.gemrender.mixin.GemRenderMixinPlugin",
        "gemrender_mixin_config" to if (mcVersion.substringBefore('.').toInt() >= 26) "[[mixins]]\nconfig = \"golemcore.gemrender.mixins.json\"" else "",
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
    // Only Forge generates a refmap; naming an absent one leaves the jar pointing at a file it lacks.
    filesMatching("*.mixins.json") { filter { if (it.trimStart().startsWith("\"refmap\"")) null else it } }
    exclude(
        "fabric.mod.json", "golemcore.fabric.mixins.json", "META-INF/mods.toml",
        "META-INF/accesstransformer.cfg", "META-INF/golemcore.neoforge.accesstransformer.cfg",
        "META-INF/golemcore.forge.accesstransformer.cfg",
    )
    // Golem Core's KubeJS plugin list names classes that exist only where the `kubejs` constant holds.
    if ((findProperty("deps.kubejs") as String?).isNullOrBlank()) exclude("kubejs.plugins.txt")
    dependsOn(mergeAccessTransformers)
    from(mergedAccessTransformer) { into("META-INF") }
    // The golem_* shaders use 26.1's pipeline format; older nodes draw the same effects with *_legacy shaders.
    if (mcVersion.substringBefore('.').toInt() < 26) {
        exclude("assets/golemcore/shaders/core/golem_*")
    }
}

// Stonecutter writes this project's sources from the root src/ tree; the toolkit must not read them
// before that has happened.
tasks.named("createMinecraftArtifacts") { dependsOn(tasks.named("stonecutterGenerate")) }

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "${base.archivesName.get()}-$mcVersion"
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

    // GemRender, an optional client peer: dev.golem.core.gemrender instances gem-declared machines through the
    // Flywheel it carries. Compiled against, never shipped or required.
    if (localGemRender != null) compileOnly(localGemRender)
    else propOrNull("deps.gemrender")?.let { compileOnly("curse.maven:gemrender-1735539:$it") }
    propOrNull("deps.gemrender_flywheel")?.let { compileOnly(it) }
    propOrNull("deps.gemrender_sodium_api")?.let { compileOnly("net.caffeinemc:sodium-neoforge-api:$it") { isTransitive = false } }

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

    // Jade, compile-only and per-node optional: dev.golem.core.jade shows dev.golem.core.info declarations in Jade's
    // HUD (26.x and 15.x API lines). A node without deps.jade compiles the adapter out (`jade`).
    (findProperty("deps.jade") as String?)?.takeIf { it.isNotBlank() }?.let { compileOnly("maven.modrinth:jade:$it") }

    // Applied Energistics 2, compile-only and per-node optional: dev.golem.core.ae2 serves dev.golem.core.autocraft
    // (crafting machines on AE2's lookup, storage cell contents). A node without deps.ae2 compiles it out (`ae2`).
    (findProperty("deps.ae2") as String?)?.takeIf { it.isNotBlank() }?.let {
        compileOnly("maven.modrinth:ae2:$it") { isTransitive = false }
    }

    // Mo' Bends, compile-only and per-node optional: its public skeleton API answers dev.golem.core.render.BodyAnimations
    // (src/mobends, added below). Never shipped or required.
    (findProperty("deps.mobends") as String?)?.takeIf { it.isNotBlank() }?.let {
        compileOnly("maven.modrinth:mo-bends-unofficial-modern-port:$it") { isTransitive = false }
    }

    // KubeJS, compile-only and per-node optional: dev.golem.core.kubejs turns every RecipeTables table into a script
    // recipe schema and serves dev.golem.core.script's bindings and events (8.x on 26.1, 7.x on 1.21.1). Rhino is the
    // script engine KubeJS's API names. A node without deps.kubejs compiles the adapter out (`kubejs`).
    (findProperty("deps.kubejs") as String?)?.takeIf { it.isNotBlank() }?.let { kubejs ->
        compileOnly("dev.latvian.mods:kubejs-neoforge:$kubejs") { isTransitive = false }
        compileOnly("dev.latvian.mods:rhino:${prop("deps.kubejs_rhino")}") { isTransitive = false }
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

// The uploaded copy of this node's jar, without the test harness.
apply(from = rootProject.file("gradle/release-jar.gradle.kts"))

// Credits and links in the loader metadata, from the mod.* properties.
apply(from = rootProject.file("gradle/mod/mod-metadata.gradle.kts"))
// Modrinth + CurseForge upload of this node's jar; run every node with the root `publishAllMods`.
apply(from = rootProject.file("gradle/mod/publish.gradle.kts"))

tasks.jar {
    from(files(rootProject.file("LICENSE"), rootProject.file("LICENSE.LESSER"), rootProject.file("CREDITS.txt"))) { rename { "${it}_${prop("mod.id")}" } }
}
