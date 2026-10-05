plugins {
    id("java-library")
    id("maven-publish")
    id("net.neoforged.moddev.legacyforge") version "2.0.141"
}

fun prop(name: String): String = property(name) as String

val mcVersion = stonecutter.current.version
val javaVersion = prop("java_version").toInt()

group = prop("mod.group")
version = "${prop("mod.version")}+$mcVersion"
base.archivesName = "${prop("mod.id")}-forge-$mcVersion"

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
    maven("https://maven.squiddev.cc") {
        name = "SquidDev"
        content { includeGroup("cc.tweaked") }
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
        // Forge 1.20.1 routes carts through getRailMaxSpeed/onMinecartPass itself.
        "rail_mixins" to "",
        "world_effect_mixins" to "",
        // Skies.Modifier: 26.1 appends environment-attribute layers; older nodes wrap the sky darkening and the
        // client colour getters (the mixin classes are empty stubs on the other era).
        "sky_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) ", \"MixinEnvironmentSkyLayers\"" else ", \"MixinLevelSkyDarken\"",
        "sky_client_mixins" to if (mcVersion.substringBefore('.').toInt() >= 26) "" else
            ", \"MixinClientLevelSky\", \"MixinDimensionEffectsSky\", \"MixinOverworldFogColor\"",
        // No vendored engine below 26.1, so nothing to feed.
        "flywheel_be_mixins" to "",
        "pack_format" to prop("pack_format"),
        "pack_format_entry" to "\"pack_format\": ${prop("pack_format")}",
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta", "*.mixins.json")) { expand(props) }
    exclude(
        "fabric.mod.json", "golemcore.fabric.mixins.json", "META-INF/neoforge.mods.toml",
        "META-INF/accesstransformer.cfg", "META-INF/golemcore.forge.accesstransformer.cfg",
        "META-INF/golemcore.neoforge.accesstransformer.cfg",
        // The flywheel widenings and golem_* shaders belong to the
        // NeoForge-only vendored backend. Keep unrelated Core shaders such as depth_merge.
        "META-INF/golemcore.flywheel.accesstransformer.cfg",
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
    // JEI, compile-only and per-node optional — same contract as the other loaders' buildscripts.
    // The facade in dev.golem.core.jei hides the 15.x/19.x/29.x API split from consumers, so this
    // library has to see whichever line the node targets; a node that declares no coordinates
    // compiles the facade out entirely (the `jei` constant).
    val jeiMc = (findProperty("deps.jei_mc") as String?)?.takeIf { it.isNotBlank() }
    val jeiVer = (findProperty("deps.jei") as String?)?.takeIf { it.isNotBlank() }
    if (jeiMc != null && jeiVer != null) compileOnly("mezz.jei:jei-$jeiMc-common-api:$jeiVer")

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
}

// Stonecutter writes the shared resources in place; the legacy toolkit must see the active node's
// access-transformer resource before it creates the transformed Minecraft artifacts.
tasks.named("createMinecraftArtifacts") { dependsOn(tasks.named("stonecutterGenerate")) }

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.named("stonecutterGenerateTest"))
}

tasks.jar {
    manifest.attributes["MixinConfigs"] = "golemcore.mixins.json"
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }
}
