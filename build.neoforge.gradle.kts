plugins {
    id("java-library")
    id("maven-publish")
    id("net.neoforged.moddev") version "2.0.148"
}

fun prop(name: String): String = property(name) as String
fun propOrNull(name: String): String? = (findProperty(name) as String?)?.takeIf { it.isNotBlank() }

val mcVersion = stonecutter.current.version
apply(from = rootProject.file("gradle/mod/gemrender-local.gradle.kts"))
val localGemRender = extra.properties["golem.gemrender.local"]

// A node with no GemRender build compiles Core's GemRender entry points from src/gemrender-absent instead.
val gemrenderAbsent = rootProject.file("src/gemrender-absent/java")
if (localGemRender == null && (findProperty("deps.gemrender") as String?).isNullOrBlank()) {
    sourceSets["main"].java.srcDir(gemrenderAbsent)
    sourceSets["main"].java.exclude {
        !it.file.toPath().startsWith(gemrenderAbsent.toPath()) && it.relativePath.pathString.startsWith("dev/golem/core/gemrender/")
    }
}
val javaVersion = prop("java_version").toInt()
// The API era the tree's guards split at: 1.21.11 already has 26.1's shapes (only its obfuscation differs).
val modern = stonecutter.eval(mcVersion, ">=1.21.11")
val modelEra = if (modern) "modern" else "legacy"
val modelRoots = if (modelEra == "modern") listOf("models-modern")
    else listOf("models-legacy", "models-legacy-neoforge")
for (modelRoot in modelRoots) {
    val modelSources = sourceSets.create(modelRoot)
    sourceSets["main"].java.source(modelSources.java)
}


// Sable (see the dependency block below) on the nodes that declare its coordinates.
val sableMc = (findProperty("deps.sable_mc") as String?)?.takeIf { it.isNotBlank() }
val sableVer = (findProperty("deps.sable") as String?)?.takeIf { it.isNotBlank() }
val sableCompanionVer = (findProperty("deps.sable_companion") as String?)?.takeIf { it.isNotBlank() }
val hasSable = sableMc != null && sableVer != null && sableCompanionVer != null

// NeoForge reads exactly one META-INF/accesstransformer.cfg per jar; it is assembled from whichever sources
// apply to this node. They are read as Stonecutter wrote them for the node, since the replacement rules rename
// classes in them.
fun nodeAccessTransformer(name: String, applies: Boolean) =
    if (applies && file("../../src/main/resources/META-INF/$name").exists())
        layout.buildDirectory.file("generated/stonecutter/main/resources/META-INF/$name").get().asFile
    else null
val accessTransformerFiles = listOfNotNull(
    nodeAccessTransformer("golemcore.neoforge.accesstransformer.cfg", true),
    nodeAccessTransformer("golemcore.legacy.neoforge.accesstransformer.cfg", !modern),
    nodeAccessTransformer("golemcore.modern.neoforge.accesstransformer.cfg", modern),
    nodeAccessTransformer("golemcore.26.2.neoforge.accesstransformer.cfg", stonecutter.eval(mcVersion, ">=26.2")),
    nodeAccessTransformer("golemcore.26.3.neoforge.accesstransformer.cfg", stonecutter.eval(mcVersion, ">=26.3")),
)
// The dev-only probe mod (src/probe, golemprobe): content that exercises Core in dev runs and GameTests without a content
// mod. Modern nodes only, where Core's GameTests use vanilla's empty structure. Never part of a jar.
val probe = if (!modern) null else sourceSets.create("probe") {
    java.exclude("dev/golem/probe/platform/fabric/**")
    compileClasspath += sourceSets["main"].output + sourceSets["main"].compileClasspath
    runtimeClasspath += sourceSets["main"].output + sourceSets["main"].runtimeClasspath
}

val mergedAccessTransformer = layout.buildDirectory.file("golemcore-at/accesstransformer.cfg")
val mergeAccessTransformers = tasks.register("mergeAccessTransformers") {
    dependsOn("stonecutterGenerate")
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

    // Node projects live in versions/<node>/ and have no src/ of their own â€” the shared tree is two
    // levels up. A bare "src/main/..." here resolves to nothing, and because the declaration is
    // guarded on exists() that failure is silent: the transformer is simply never applied.
    for (at in accessTransformerFiles) accessTransformers.from(at)

    runs {
        configureEach {
            ideName = "NeoForge ${name.replaceFirstChar { it.uppercase() }} ($mcVersion)"
            if (probe != null) sourceSet = probe
            // Dev runs warn on every peer that still uses @OnlyIn (REI does) and hold the client on that screen.
            systemProperty("neoforge.warnings.onlyin.hide", "true")
        }
        // Headless GameTests: `gradlew :<node>-neoforge:runGameTestServer` (the probe's tests).
        if (probe != null) register("gameTestServer") {
            type = "gameTestServer"
            gameDirectory = file("runs/gametest")
        }
        register("client") {
            client()
            gameDirectory = file("runs/client")
            // -Pgolem.run.args="--quickPlayMultiplayer localhost:25601" and the like, for scripted dev launches.
            propOrNull("golem.run.args")?.let { programArguments.addAll(it.split(' ')) }
        }
        register("server") {
            server()
            gameDirectory = file("runs/server")
        }
    }

    mods {
        register(prop("mod.id")) { sourceSet(sourceSets["main"]) }
        if (probe != null) register("golemprobe") { sourceSet(probe) }
    }

    // Unit tests compile against Minecraft too â€” the data backends are built on CompoundTag/NbtOps.
    // MDG only wires the modding dependencies into `main` by default.
    addModdingDependenciesTo(sourceSets["test"])

    // And some of them have to *run* under FML. NeoForge's SharedConstants asks FMLEnvironment
    // whether it is in production during its static init, which throws "There is no current FML
    // Loader" in a plain test JVM â€” so any test that touches Bootstrap or a vanilla registry dies
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
    // OpenComputers: Rebooted's API jar (the optional `opencomputers` peer, see deps.oc).
    exclusiveContent {
        forRepository { maven("https://maven.michiyo.me/releases") { name = "Michiyo" } }
        filter { includeGroup("li.cil.oc") }
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
        "legacy_item_owner_mixins" to if (!modern)
            ", \"MixinItemRenderer\"" else "",
        // EntityTicks: NeoForge posts EntityTickEvent.Post itself.
        "entity_tick_mixins" to "",
        // Respawns: 1.21+ picks the point in ServerPlayer (MixinRespawnSelection).
        "legacy_respawn_mixins" to "",
        "physics_mixins" to "",
        // Chunks.loadEntityTicking before 26.1 (EntitySectionsAccess).
        "entity_load_mixins" to if (!modern) ", \"EntitySectionsAccess\"" else "",
        // dev.golem.core.gametest.TestChunks: a 26.1 test waits until its whole plot is entity-ticking.
        "gametest_mixins" to if (modern) ", \"MixinGameTestChunks\"" else "",
        // NeoForge 1.21.1 still calls getRailMaxSpeed/onMinecartPass; 26.1 declares but no longer calls them.
        "rail_mixins" to if (modern) ", \"MixinMinecartRails\"" else "",
        // 1.21.1 only: the world-exit unload spin (MixinChunkMapUnloadDrain).
        "unload_mixins" to if (mcVersion == "1.21.1") ", \"MixinChunkMapUnloadDrain\"" else "",
        // Gliders: 26.1 has canGlide (covers the starts too); 1.21.1 asks the chest item at three points.
        "glider_mixins" to if (modern) ", \"MixinGliderFlight\"" else ", \"MixinGliderFlight\", \"MixinGliderStart\"",
        "glider_client_mixins" to if (modern) "" else ", \"MixinGliderClientStart\"",
        // SlotSync: from 26.1 the client echoes server slot pushes from the creative screen.
        "creative_echo_mixins" to if (modern) ", \"MixinCreativeEchoPush\", \"MixinCreativeEchoListener\"" else "",
        // 26.3 has neither the GL encoder hook nor Iris.
        "world_effect_mixins" to if (!modern) "" else if (stonecutter.eval(mcVersion, ">=26.3")) ", \"MixinWorldEffects\""
            else ", \"MixinWorldEffects\", \"MixinNativeWorldTarget\"",
        // Dev-only, listed in the harness mixin config that releaseJar strips.
        "client_gametest_mixins" to "",
        "render_validation_mixins" to "",
        // 1.21 paces the server through its tick-rate manager; NeoForge posts RenderFrameEvent.Post.
        "harness_clock_mixins" to "",
        "harness_frame_mixins" to "",
        // Skies.Modifier: 26.1 appends environment-attribute layers; older nodes wrap the sky darkening and the
        // client colour getters (the mixin classes are empty stubs on the other era).
        "sky_mixins" to if (modern) ", \"MixinEnvironmentSkyLayers\"" else ", \"MixinLevelSkyDarken\"",
        "sky_client_mixins" to if (modern) "" else
            ", \"MixinClientLevelSky\", \"MixinDimensionEffectsSky\", \"MixinOverworldFogColor\"",
        // Replacement clouds use NeoForge's hook; additions append after the native renderer on both eras.
        // NeoForge 1.21.11 never calls its custom weather renderers (see MixinWeatherRender), so Core hooks the weather there.
        "cloud_mixins" to if (mcVersion == "1.21.11") ", \"MixinCloudRender\", \"MixinWeatherRender\""
            else if (modern) ", \"MixinCloudRender\"" else ", \"MixinLegacyCloudRender\", \"MixinLegacyWeatherRender\"",
        // The Sable integration's own mixin config (src/sable/resources), only on nodes that declare Sable.
        "sable_mixins" to if (hasSable) "[[mixins]]\nconfig = \"golemcore.sable.mixins.json\"" else "",
        "pack_format" to prop("pack_format"),
        "pack_format_entry" to if (modern)
            "\"min_format\": ${prop("pack_format")}, \"max_format\": ${prop("pack_format")}"
        else
            "\"pack_format\": ${prop("pack_format")}",
    )
    inputs.properties(props)
    // 1.21.11's one pack.mcmeta cannot be valid for both its data format (needs supported_formats) and its
    // resource format (forbids it); without the file the loader describes the mod's pack itself.
    if (!(findProperty("pack_format_data") as String?).isNullOrBlank()) exclude("pack.mcmeta")
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
    if (!modern) {
        exclude("assets/golemcore/shaders/core/golem_*")
    }
    // 26.3 compiles every shader to SPIR-V: its copies (explicit locations, #include) replace the shared ones.
    if (stonecutter.eval(mcVersion, ">=26.3")) {
        val shaders263 = rootProject.file("src/shaders-26.3")
        exclude { !it.file.toPath().startsWith(shaders263.toPath()) && File(shaders263, it.relativePath.pathString).isFile }
        from(shaders263)
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
tasks.compileJava {
    dependsOn(tasks.named("stonecutterGenerate"))
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "2000"))
}
tasks.processResources { dependsOn(tasks.named("stonecutterGenerate")) }

// On modern nodes, `-Ppeers.run=true` puts the node's optional peers (JEI, REI, Jade, CC: Tweaked, AE2 with GuideME) in the client and
// server runs, beside the probe's content for them. They carry mixins, so they go to the run's mods folder; a run without
// the flag empties it again. Never compiled against from here, shipped or required.
val peerMods: Configuration by configurations.creating
// `-Ppeers.skip=cc,rei` leaves named peers out (jei, rei, jade, cc, ae2): two peers whose own builds clash, say.
val skippedPeers = propOrNull("peers.skip")?.split(',')?.map { it.trim() }.orEmpty()
if (modern && propOrNull("peers.run") == "true") dependencies {
    val jeiMc = propOrNull("deps.jei_mc")
    if ("jei" !in skippedPeers) propOrNull("deps.jei")?.takeIf { jeiMc != null }?.let { peerMods("mezz.jei:jei-$jeiMc-neoforge:$it") { isTransitive = false } }
    if ("jei" !in skippedPeers) propOrNull("deps.jei_config")?.takeIf { jeiMc != null }?.let { peerMods("net.mezzdev.config:mezz_config-$jeiMc-neoforge:$it") { isTransitive = false } }
    if ("jei" !in skippedPeers) propOrNull("deps.jei_config_gui")?.takeIf { jeiMc != null }?.let { peerMods("net.mezzdev.config:mezz_config_gui-$jeiMc-neoforge:$it") { isTransitive = false } }
    if ("rei" !in skippedPeers) propOrNull("deps.rei")?.let { peerMods("me.shedaniel:RoughlyEnoughItems-neoforge:$it") { isTransitive = false } }
    if ("rei" !in skippedPeers) propOrNull("deps.rei_cloth")?.let { peerMods("me.shedaniel.cloth:cloth-config-neoforge:$it") { isTransitive = false } }
    if ("rei" !in skippedPeers) propOrNull("deps.rei_architectury")?.let { peerMods("dev.architectury:architectury-neoforge:$it") { isTransitive = false } }
    if ("jade" !in skippedPeers) propOrNull("deps.jade")?.let { peerMods("maven.modrinth:jade:$it") }
    val ccMc = propOrNull("deps.cc_mc")
    if ("cc" !in skippedPeers) propOrNull("deps.cc")?.takeIf { ccMc != null }?.let { peerMods("cc.tweaked:cc-tweaked-$ccMc-forge:$it") { isTransitive = false } }
    if ("ae2" !in skippedPeers) propOrNull("deps.ae2")?.let { peerMods("maven.modrinth:ae2:$it") { isTransitive = false } }
    if ("ae2" !in skippedPeers) propOrNull("deps.ae2_guideme")?.let { peerMods("maven.modrinth:guideme:$it") { isTransitive = false } }
}
if (modern) for (run in listOf("client", "server")) {
    val modsDir = layout.projectDirectory.dir("runs/$run/mods")
    val sync = tasks.register("syncPeerMods${run.replaceFirstChar { it.uppercase() }}") {
        inputs.files(peerMods)
        outputs.dir(modsDir)
        doLast { project.sync { from(peerMods); into(modsDir) } }
    }
    val task = "run${run.replaceFirstChar { it.uppercase() }}"
    tasks.matching { it.name == task || it.name == "prepare${run.replaceFirstChar { it.uppercase() }}Run" }.configureEach { dependsOn(sync) }
}
if (probe != null) {
    tasks.named<ProcessResources>("processProbeResources") {
        val probeProps = mapOf("mod_version" to prop("mod.version"), "mod_author" to prop("mod.author"),
            "mod_license" to prop("mod.license"), "neoforge_loader_range" to prop("deps.neoforge_loader_range"))
        inputs.properties(probeProps)
        filesMatching("META-INF/neoforge.mods.toml") { expand(probeProps) }
        exclude("fabric.mod.json")
        dependsOn(tasks.named("stonecutterGenerate"))
    }
    tasks.named<JavaCompile>("compileProbeJava") { dependsOn(tasks.named("stonecutterGenerate")) }
    tasks.matching { it.name == "runGameTestServer" }.configureEach {
        doFirst { delete(layout.projectDirectory.dir("runs/gametest/world")) }
    }
}

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

    // EMI, compile-only and per-node optional: dev.golem.core.emi feeds the same viewer declarations to EMI.
    (findProperty("deps.emi") as String?)?.takeIf { it.isNotBlank() }?.let { compileOnly("maven.modrinth:emi:$it") }

    // Jade, compile-only and per-node optional: dev.golem.core.jade shows dev.golem.core.info declarations in Jade's
    // HUD (26.x and 15.x API lines). A node without deps.jade compiles the adapter out (`jade`).
    (findProperty("deps.jade") as String?)?.takeIf { it.isNotBlank() }?.let { compileOnly("maven.modrinth:jade:$it") }
    // OpenComputers: Rebooted, compile-only: dev.golem.core.opencomputers serves dev.golem.core.computer components to
    // OC cables and Adapters. A node without deps.oc compiles the adapter out (`opencomputers`).
    (findProperty("deps.oc") as String?)?.takeIf { it.isNotBlank() }?.let {
        compileOnly("li.cil.oc:opencomputers:$it:api") { isTransitive = false }
    }

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
apply(from = rootProject.file("gradle/gemrender-api.gradle.kts"))
apply(from = rootProject.file("gradle/mod/mixin-validation.gradle.kts"))
// Launch capture for the in-game harness (harness.json, harness/scenarios); inert without -Pharness.spec.
apply(from = rootProject.file("gradle/mod/harness-spec.gradle.kts"))

// Credits and links in the loader metadata, from the mod.* properties.
apply(from = rootProject.file("gradle/mod/mod-metadata.gradle.kts"))
// Modrinth + CurseForge upload of this node's jar; run every node with the root `publishAllMods`.
apply(from = rootProject.file("gradle/mod/publish.gradle.kts"))

tasks.jar {
    from(files(rootProject.file("LICENSE"), rootProject.file("LICENSE.LESSER"), rootProject.file("CREDITS.txt"))) { rename { "${it}_${prop("mod.id")}" } }
}
