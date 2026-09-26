plugins {
    id("dev.golem.node")
    id("maven-publish")
}

val mcVersion = stonecutter.current.version
// Node projects sit in versions/<node>/; the shared tree is two levels up (a bare "src/..." resolves to nothing).
val accessTransformer = file("../../src/main/resources/META-INF/golemcore.forge.accesstransformer.cfg")

java { withSourcesJar() }

repositories {
    maven("https://maven.blamejared.com") { name = "BlameJared" }
}

legacyForge {
    accessTransformers.from(accessTransformer)
    validateAccessTransformers = true
    addModdingDependenciesTo(sourceSets["test"])
}

golem.resourceProperties.putAll(mapOf(
    "legacy_item_owner_mixins" to ", \"MixinItemRenderer\"",
    "world_effect_mixins" to "",
    "flywheel_be_mixins" to "",
))

tasks.processResources {
    exclude(
        "META-INF/accesstransformer.cfg", "META-INF/golemcore.forge.accesstransformer.cfg",
        "META-INF/golemcore.neoforge.accesstransformer.cfg",
        // NeoForge-only vendored backend.
        "META-INF/golemcore.flywheel.accesstransformer.cfg",
        "assets/golemcore/shaders/core/golem_*",
    )
    from(accessTransformer) {
        into("META-INF")
        rename { "accesstransformer.cfg" }
    }
}

dependencies {
    // dev.golem.core.jei hides the JEI 15.x/19.x/29.x split; blank `deps.jei` compiles it out.
    val jeiMc = (findProperty("deps.jei_mc") as String?)?.takeIf { it.isNotBlank() }
    val jeiVer = (findProperty("deps.jei") as String?)?.takeIf { it.isNotBlank() }
    if (jeiMc != null && jeiVer != null) compileOnly("mezz.jei:jei-$jeiMc-common-api:$jeiVer")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.named("stonecutterGenerateTest"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }
}
