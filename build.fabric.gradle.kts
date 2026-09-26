plugins {
    id("dev.golem.node")
    id("maven-publish")
}

val mcVersion = stonecutter.current.version
val obfuscated = mcVersion.substringBefore('.').toInt() < 26
// Vendored instancing backend = 26.1 blaze3d GPU-buffer API; its GlBuffer widenings live in golemcore.accesswidener.
val hasVendoredFlywheel = !obfuscated

java { withSourcesJar() }

repositories {
    maven("https://maven.blamejared.com") { name = "BlameJared" }
}

golem.resourceProperties.putAll(mapOf(
    "legacy_item_owner_mixins" to if (obfuscated) ", \"MixinItemRenderer\"" else "",
    "world_effect_mixins" to if (!obfuscated) ", \"MixinWorldEffects\"" else "",
    // fabric-api reports client block entities; no mixin.
    "flywheel_be_mixins" to "",
    // No debug-screen entry registry on Fabric.
    "debug_line_mixins" to if (hasVendoredFlywheel) ", \"MixinDebugScreenLines\"" else "",
    // 1.20.1 lacks the pre-removal explosion hook (1.21 onExplosionHit); see CompatBlock.
    "legacy_explosion_mixins" to if (mcVersion.startsWith("1.20.")) "\"MixinExplosionBlockHook\"" else "",
    // 1.20 fabric-api stacks mod resource packs in load order; dependents must override dependencies.
    "mod_pack_order_mixins" to if (mcVersion.startsWith("1.20.")) ", \"MixinModResourcePackOrder\"" else "",
    // No public dynamic GameTest registration on 26.1 Fabric; follows the (empty there) legacy explosion entry.
    "fabric_gametest_mixins" to if (!obfuscated) "\"MixinFabricGameTestModInitializer\"" else "",
    "legacy_client_mixins" to ", \"MixinMouseInput\"" + if (obfuscated) ", \"MixinLegacyCrosshair\"" else "",
))

tasks.processResources {
    exclude(
        "META-INF/accesstransformer.cfg",
        "META-INF/golemcore.neoforge.accesstransformer.cfg",
        "META-INF/golemcore.forge.accesstransformer.cfg",
        "META-INF/golemcore.flywheel.accesstransformer.cfg",
    )
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
