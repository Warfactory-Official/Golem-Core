plugins {
    id("java-library")
    id("maven-publish")
    id("net.neoforged.moddev.legacyforge") version "2.0.141"
}

fun prop(name: String): String = property(name) as String

val mcVersion = stonecutter.current.version
apply(from = rootProject.file("gradle/mod/gemrender-local.gradle.kts"))
val localGemRender = extra.properties["golem.gemrender.local"]
val javaVersion = prop("java_version").toInt()
val modelEra = if (stonecutter.eval(mcVersion, ">=26.1")) "modern" else "legacy"
val modelRoots = if (modelEra == "modern") listOf("models-modern")
    else listOf("models-legacy", "models-legacy-forge")
for (modelRoot in modelRoots) {
    val modelSources = sourceSets.create(modelRoot)
    sourceSets["main"].java.source(modelSources.java)
}


group = prop("mod.group")
version = "${prop("mod.version")}+$mcVersion"
base.archivesName = "${prop("mod.id")}-forge"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

legacyForge {
    version = prop("deps.forge")
    // Node projects live in versions/<node>/ and have no src/ of their own — the shared tree is two
    // levels up. A bare "src/main/..." resolves to nothing, and the exists() guard makes that silent.
    val at = file("../../src/main/resources/META-INF/golemcore.forge.accesstransformer.cfg")
    if (at.exists()) accessTransformers.from(at)
    validateAccessTransformers = true
    addModdingDependenciesTo(sourceSets["test"])
    runs {
        configureEach {
            ideName = "Forge ${name.replaceFirstChar { it.uppercase() }} ($mcVersion)"
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

tasks.processResources {
    val props = mapOf(
        "mod_id" to prop("mod.id"),
        "mod_name" to prop("mod.name"),
        "mod_version" to prop("mod.version"),
        "mod_author" to prop("mod.author"),
        "mod_license" to prop("mod.license"),
        "mod_description" to prop("mod.description"),
        "minecraft_range" to prop("deps.minecraft_range"),
        "forge_loader_range" to prop("deps.forge_loader_range"),
        "java_version" to javaVersion.toString(),
        "mixin_compatibility" to if (javaVersion <= 17) "JAVA_17" else "JAVA_21",
        "legacy_item_owner_mixins" to if (mcVersion.substringBefore('.').toInt() < 26)
            ", \"MixinItemRenderer\"" else "",
        // EntityTicks: NeoForge's EntityTickEvent.Post has no Fabric or Forge counterpart.
        "entity_tick_mixins" to ", \"MixinEntityTicks\"",
        // Respawns: 1.20.1 picks the point in PlayerList.respawn, 1.21+ in ServerPlayer (MixinRespawnSelection).
        "legacy_respawn_mixins" to if (stonecutter.eval(mcVersion, "<1.21")) ", \"MixinPlayerListRespawns\"" else "",
        // The 1.20.5 physics attributes, backported on 1.20.1 only (dev.golem.core.world.Physics).
        "physics_mixins" to if (mcVersion.startsWith("1.20.")) ", \"MixinLegacyAttributeSupplier\", \"MixinLegacyLivingPhysics\"" else "",
        // Chunks.loadEntityTicking before 26.1 (EntitySectionsAccess).
        "entity_load_mixins" to if (mcVersion.substringBefore('.').toInt() < 26) ", \"EntitySectionsAccess\"" else "",
        // dev.golem.core.gametest.TestChunks: a 26.1 test waits until its whole plot is entity-ticking.
        "gametest_mixins" to "",
        // Forge 1.20.1 routes carts through getRailMaxSpeed/onMinecartPass itself.
        "rail_mixins" to "",
        // 1.21.1 only: the world-exit unload spin (MixinChunkMapUnloadDrain).
        "unload_mixins" to "",
        // Gliders: the chest item's canElytraFly/elytraFlightTick (flight + server start) and the client start check.
        "glider_mixins" to ", \"MixinGliderFlight\", \"MixinGliderStart\"",
        "glider_client_mixins" to ", \"MixinGliderClientStart\"",
        // SlotSync: from 26.1 the client echoes server slot pushes from the creative screen.
        "creative_echo_mixins" to "",
        "world_effect_mixins" to "",
        // Dev-only, listed in the harness mixin config that releaseJar strips.
        "client_gametest_mixins" to "",
        "render_validation_mixins" to "",
        // 1.21 paces the server through its tick-rate manager; only Fabric lacks a render-end event.
        "harness_clock_mixins" to if (stonecutter.eval(mcVersion, "<1.21")) ", \"MixinHarnessServerClock\"" else "",
        "harness_frame_mixins" to "",
        // Skies.Modifier: 26.1 appends environment-attribute layers; older nodes wrap the sky darkening and the
        // client colour getters (the mixin classes are empty stubs on the other era).
        "sky_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinEnvironmentSkyLayers\"" else ", \"MixinLevelSkyDarken\"",
        "sky_client_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) "" else
            ", \"MixinClientLevelSky\", \"MixinDimensionEffectsSky\", \"MixinOverworldFogColor\"",
        // Clouds: Forge's only seam is the effects object's renderClouds (see MixinLegacyCloudRender).
        "cloud_mixins" to ", \"MixinLegacyCloudRender\", \"MixinLegacyWeatherRender\"",
        "gemrender_mixins" to "",
        "gemrender_mixin_plugin" to "dev.golem.core.gemrender.mixin.GemRenderMixinPlugin",
        "pack_format" to prop("pack_format"),
        "pack_format_entry" to "\"pack_format\": ${prop("pack_format")}",
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta", "*.mixins.json")) { expand(props) }
    // Golem Core's KubeJS plugin list names classes that exist only where the `kubejs` constant holds.
    if ((findProperty("deps.kubejs") as String?).isNullOrBlank()) exclude("kubejs.plugins.txt")
    exclude(
        "fabric.mod.json", "golemcore.fabric.mixins.json", "META-INF/neoforge.mods.toml",
        "META-INF/accesstransformer.cfg", "META-INF/golemcore.forge.accesstransformer.cfg",
        "META-INF/golemcore.neoforge.accesstransformer.cfg",
        // golem_* shaders use 26.1's pipeline format; this node draws the same effects with *_legacy shaders.
        "assets/golemcore/shaders/core/golem_*",
    )
    from(file("../../src/main/resources/META-INF/golemcore.forge.accesstransformer.cfg")) {
        into("META-INF")
        rename { "accesstransformer.cfg" }
    }
}

// The toolkit reads this project's sources, so it must not run before stonecutter has written
// them. The NeoForge buildscript has always declared this; without it here, any invocation that
// runs both tasks (chiseledBuild) fails Gradle's implicit-dependency validation.
tasks.named("createMinecraftArtifacts") { dependsOn(tasks.named("stonecutterGenerate")) }

tasks.compileJava {
    dependsOn(tasks.named("stonecutterGenerate"))
    options.release = javaVersion
}
tasks.processResources { dependsOn(tasks.named("stonecutterGenerate")) }

dependencies {
    compileOnly("org.jspecify:jspecify:1.0.0")
    compileOnly("org.spongepowered:mixin:0.8.5")
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    // MixinExtras (@WrapOperation, @ModifyReturnValue). Unlike Fabric Loader and NeoForge, Forge 1.20.1 does not
    // bundle it (its installer lists only Mixin 0.8.5, through 47.4.26), so Core nests it (Jar-in-Jar; FML picks the
    // newest copy any mod nests) and puts it on the dev runtime of Core and of mods built on it. The common
    // annotation processor writes its injectors into the refmap.
    compileOnly("io.github.llamalad7:mixinextras-common:0.3.5")

    // GemRender, an optional client peer: dev.golem.core.gemrender instances gem-declared machines through the
    // Flywheel it carries. SRG-mapped jars, hence modCompileOnly. Never shipped or required.
    if (localGemRender != null) modCompileOnly(localGemRender)
    else (findProperty("deps.gemrender") as String?)?.takeIf { it.isNotBlank() }?.let { modCompileOnly("curse.maven:gemrender-1735539:$it") }
    (findProperty("deps.gemrender_flywheel") as String?)?.takeIf { it.isNotBlank() }?.let { modCompileOnly(it) }
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.3.5")
    implementation("io.github.llamalad7:mixinextras-forge:0.5.5")
    jarJar("io.github.llamalad7:mixinextras-forge:[0.3.5,)") { version { prefer("0.5.5") } }
    // JEI, compile-only and per-node optional — same contract as the other loaders' buildscripts.
    // The facade in dev.golem.core.jei hides the 15.x/19.x/29.x API split from consumers, so this
    // library has to see whichever line the node targets; a node that declares no coordinates
    // compiles the facade out entirely (the `jei` constant).
    val jeiMc = (findProperty("deps.jei_mc") as String?)?.takeIf { it.isNotBlank() }
    val jeiVer = (findProperty("deps.jei") as String?)?.takeIf { it.isNotBlank() }
    if (jeiMc != null && jeiVer != null) compileOnly("mezz.jei:jei-$jeiMc-common-api:$jeiVer")

    // REI, compile-only and per-node optional, like JEI: the adapter in dev.golem.core.rei feeds a mod's neutral
    // viewer declarations (dev.golem.core.viewer) to REI 12. Its Forge jars are SRG-mapped, hence `modCompileOnly`.
    (findProperty("deps.rei") as String?)?.takeIf { it.isNotBlank() }?.let { rei ->
        modCompileOnly("me.shedaniel:RoughlyEnoughItems-api-forge:$rei")
        // MDG Legacy remaps the API jar alone, not its POM dependencies; REI's API types name Cloth Config's and
        // Architectury's (Point, Rectangle, FluidStack), so they are compiled against as well.
        (findProperty("deps.rei_cloth") as String?)?.takeIf { it.isNotBlank() }?.let { modCompileOnly("me.shedaniel.cloth:cloth-config-forge:$it") }
        (findProperty("deps.rei_architectury") as String?)?.takeIf { it.isNotBlank() }?.let { modCompileOnly("dev.architectury:architectury-forge:$it") }
    }

    // Jade 11, compile-only and per-node optional (see the NeoForge buildscript); its Forge jar is SRG-mapped.
    (findProperty("deps.jade") as String?)?.takeIf { it.isNotBlank() }?.let { modCompileOnly("maven.modrinth:jade:$it") }

    // Applied Energistics 2, compile-only and per-node optional (see the NeoForge buildscript); SRG-mapped.
    (findProperty("deps.ae2") as String?)?.takeIf { it.isNotBlank() }?.let {
        modCompileOnly("maven.modrinth:ae2:$it") { isTransitive = false }
    }

    // Mo' Bends, compile-only and per-node optional (see the NeoForge buildscript); its Forge jar is SRG-mapped.
    (findProperty("deps.mobends") as String?)?.takeIf { it.isNotBlank() }?.let {
        modCompileOnly("maven.modrinth:mo-bends-unofficial-modern-port:$it") { isTransitive = false }
    }

    // KubeJS 6, compile-only and per-node optional (see the NeoForge buildscript). Its Forge jars are SRG-mapped, hence
    // `modCompileOnly`; MDG Legacy remaps each jar alone, and KubeJS 6's API names Rhino's and Architectury's types.
    (findProperty("deps.kubejs") as String?)?.takeIf { it.isNotBlank() }?.let { kubejs ->
        modCompileOnly("dev.latvian.mods:kubejs-forge:$kubejs") { isTransitive = false }
        modCompileOnly("dev.latvian.mods:rhino-forge:${prop("deps.kubejs_rhino")}") { isTransitive = false }
        (findProperty("deps.kubejs_architectury") as String?)?.takeIf { it.isNotBlank() }?.let {
            modCompileOnly("dev.architectury:architectury-forge:$it") { isTransitive = false }
        }
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
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

mixin {
    add(sourceSets["main"], "golemcore.refmap.json")
    config("golemcore.mixins.json")
    config("golemcore.harness.mixins.json")
    config("golemcore.gemrender.mixins.json")
}

// Stonecutter writes the shared resources in place; the legacy toolkit must see the active node's
// access-transformer resource before it creates the transformed Minecraft artifacts.
tasks.named("createMinecraftArtifacts") { dependsOn(tasks.named("stonecutterGenerate")) }

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.named("stonecutterGenerateTest"))
}

tasks.jar {
    manifest.attributes["MixinConfigs"] = "golemcore.mixins.json,golemcore.harness.mixins.json,golemcore.gemrender.mixins.json"
    from(files(rootProject.file("LICENSE"), rootProject.file("LICENSE.LESSER"), rootProject.file("CREDITS.txt"))) { rename { "${it}_${prop("mod.id")}" } }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "${base.archivesName.get()}-$mcVersion"
            from(components["java"])
        }
    }
}

// The uploaded copy of this node's jar, without the test harness.
apply(from = rootProject.file("gradle/release-jar.gradle.kts"))
apply(from = rootProject.file("gradle/mod/mixin-validation.gradle.kts"))

// Credits and links in the loader metadata, from the mod.* properties.
apply(from = rootProject.file("gradle/mod/mod-metadata.gradle.kts"))
// Modrinth + CurseForge upload of this node's jar; run every node with the root `publishAllMods`.
apply(from = rootProject.file("gradle/mod/publish.gradle.kts"))

// Mo' Bends (bendy player animations), an optional client peer on the nodes that declare it. Its backend of
// dev.golem.core.render.BodyAnimations lives in its own source roots, src/mobends and src/mobends-forge, added only here.
if ((findProperty("deps.mobends") as String?)?.isNotBlank() == true) {
    sourceSets["main"].java.srcDir(rootProject.file("src/mobends/java"))
    sourceSets["main"].java.srcDir(rootProject.file("src/mobends-forge/java"))
}
