plugins {
    id("dev.golem.node")
    id("maven-publish")
}

val mcVersion = stonecutter.current.version
// Vendored Flywheel backend = 26.1 blaze3d GPU-buffer API; its AT names types absent before 26.1 (fails the node).
val hasVendoredFlywheel = mcVersion.substringBefore('.').toInt() >= 26

// NeoForge reads one META-INF/accesstransformer.cfg per jar: shipped file = merge of the sources this node applies.
// Node projects sit in versions/<node>/; the shared tree is two levels up.
val accessTransformerFiles = listOfNotNull(
    file("../../src/main/resources/META-INF/golemcore.neoforge.accesstransformer.cfg").takeIf { it.exists() },
    file("../../src/main/resources/META-INF/golemcore.flywheel.accesstransformer.cfg").takeIf { hasVendoredFlywheel && it.exists() },
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

java { withSourcesJar() }

repositories {
    maven("https://maven.blamejared.com") { name = "BlameJared" }
}

neoForge {
    for (at in accessTransformerFiles) accessTransformers.from(at)
    addModdingDependenciesTo(sourceSets["test"])
    // NeoForge's SharedConstants asks FMLEnvironment during static init => registry-backed tests need FML's JUnit launcher.
    unitTest {
        enable()
        testedMod = mods.named(prop("mod.id"))
    }
}

golem.resourceProperties.putAll(mapOf(
    "legacy_item_owner_mixins" to if (mcVersion.substringBefore('.').toInt() < 26) ", \"MixinItemRenderer\"" else "",
    "world_effect_mixins" to if (hasVendoredFlywheel) ", \"MixinWorldEffects\"" else "",
    "flywheel_be_mixins" to if (hasVendoredFlywheel) ", \"MixinLevelChunkVisuals\"" else "",
))

tasks.processResources {
    exclude(
        "META-INF/accesstransformer.cfg", "META-INF/golemcore.neoforge.accesstransformer.cfg",
        "META-INF/golemcore.forge.accesstransformer.cfg",
        "META-INF/golemcore.flywheel.accesstransformer.cfg",
    )
    dependsOn(mergeAccessTransformers)
    from(mergedAccessTransformer) { into("META-INF") }
    if (!hasVendoredFlywheel) exclude("assets/golemcore/shaders/core/golem_*")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }
}

dependencies {
    // dev.golem.core.jei hides the JEI 19.x/29.x split; blank `deps.jei` compiles it out.
    val jeiMc = (findProperty("deps.jei_mc") as String?)?.takeIf { it.isNotBlank() }
    val jeiVer = (findProperty("deps.jei") as String?)?.takeIf { it.isNotBlank() }
    if (jeiMc != null && jeiVer != null) compileOnly("mezz.jei:jei-$jeiMc-common-api:$jeiVer")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.named("stonecutterGenerateTest"))
    testLogging { events("passed", "failed", "skipped") }
}

fun prop(name: String): String = property(name) as String
