// Uploads this node's release jar to Modrinth and CurseForge (mod-publish-plugin), so a release is one
// command instead of twelve manual uploads per mod. Apply it last in a loader buildscript, after the
// loader plugin has registered its jar tasks:
//
//     apply(from = rootProject.file("../Golem-Core/gradle/mod/publish.gradle.kts"))
//
// and aggregate it in stonecutter.gradle.kts with a root task depending on
// `stonecutter.tasks.named("publishMods")` (`publishAllMods` in every Golem Core project).
//
// Keys, read from the project's gradle.properties (a node's own gradle.properties may override any):
//   golem.build.publish.modrinthProject    Modrinth project id or slug; blank skips Modrinth
//   golem.build.publish.curseforgeProject  CurseForge numeric project id; blank skips CurseForge
//   golem.build.publish.requires           required dependencies, comma separated project slugs (both sites)
//   golem.build.publish.fabricRequires     extra required dependencies on Fabric nodes (fabric-api)
//   golem.build.publish.optional           optional dependencies, comma separated slugs (both sites)
//   golem.build.publish.minecraftVersions  game versions this jar is listed for; default deps.minecraft
//   golem.build.publish.skip               true leaves this node out (snapshot nodes are left out anyway)
//
// Per-run switches (-P on the command line, or ORG_GRADLE_PROJECT_<name> in CI):
//   publish.dryRun=true     builds and logs every upload without making it (no tokens needed)
//   publish.type=beta       alpha | beta | release; default beta
//   publish.changelog=...   changelog text; else publish.changelogFile (whole file), else the
//                           "## <mod.version>" section of CHANGELOG.md at the project root
//
// Tokens come from MODRINTH_TOKEN / CURSEFORGE_TOKEN or the modrinth.token / curseforge.token Gradle
// properties in ~/.gradle/gradle.properties. Never put a token in a repository file.

buildscript {
    repositories { gradlePluginPortal() }
    dependencies { classpath("me.modmuss50:mod-publish-plugin:2.2.1") }
}

apply<me.modmuss50.mpp.MppPlugin>()

fun setting(name: String): String = (findProperty("golem.build.publish.$name") as String?)?.trim().orEmpty()
fun list(name: String): List<String> = setting(name).split(',').map(String::trim).filter(String::isNotEmpty)

val loader = project.name.substringAfterLast('-')
val minecraft = property("deps.minecraft") as String
val gameVersions = list("minecraftVersions").ifEmpty { listOf(minecraft) }
val modName = property("mod.name") as String
val modVersion = property("mod.version") as String

// A snapshot node is a development target, never a release: neither site lists those game versions.
val unreleased = Regex("snapshot|-pre|-rc|w\\d", RegexOption.IGNORE_CASE).containsMatchIn(minecraft)
val skipped = setting("skip").toBoolean() || unreleased

// The jar a player installs: Loom's remapped jar on obfuscated Fabric, ModDevGradle Legacy's
// reobfuscated jar on Forge, and the plain jar everywhere else (NeoForge, unobfuscated Fabric).
val releaseJarTask = listOf("remapJar", "reobfJar").firstOrNull { it in tasks.names } ?: "jar"

val requires = list("requires") + if (loader == "fabric") list("fabricRequires") else emptyList()
val optional = list("optional")

// CHANGELOG.md keeps every release under a "## <version>" heading; only this version's section is
// uploaded. An explicit publish.changelogFile (the workflow's tag message) is used whole.
fun versionSection(text: String): String {
    val lines = text.lines()
    // "## 0.1.0", "## v0.1.0", "## [0.1.0] - 2026-10-13" all match.
    val start = lines.indexOfFirst {
        it.startsWith("## ") && it.removePrefix("## ").trim().split(' ')[0].trim('[', ']').removePrefix("v") == modVersion
    }
    if (start < 0) return ""
    val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
    return lines.subList(start + 1, end).joinToString("\n").trim()
}

val changelogText = providers.gradleProperty("publish.changelog").orElse(
    providers.gradleProperty("publish.changelogFile")
        .map { rootProject.file(it).takeIf(File::isFile)?.readText().orEmpty() }
        .orElse(provider {
            rootProject.file("CHANGELOG.md").takeIf(File::isFile)?.readText()?.let(::versionSection).orEmpty()
        })
).map { it.ifBlank { "$modName $modVersion." } }

configure<me.modmuss50.mpp.ModPublishExtension> {
    file.set(tasks.named<AbstractArchiveTask>(releaseJarTask).flatMap { it.archiveFile })
    // Unique per node: Fabric and Forge jars for one Minecraft version must not share a version number.
    version.set("$modVersion+$minecraft-$loader")
    val loaderName = mapOf("neoforge" to "NeoForge", "forge" to "Forge", "fabric" to "Fabric")[loader] ?: loader
    displayName.set("$modName $modVersion ($loaderName $minecraft)")
    changelog.set(changelogText)
    type.set(when (providers.gradleProperty("publish.type").getOrElse("beta").lowercase()) {
        "alpha" -> ALPHA
        "release", "stable" -> STABLE
        else -> BETA
    })
    modLoaders.add(loader)
    dryRun.set(providers.gradleProperty("publish.dryRun").map(String::toBoolean).orElse(false))

    if (skipped) return@configure

    if (setting("modrinthProject").isNotEmpty()) modrinth {
        projectId.set(setting("modrinthProject"))
        accessToken.set(providers.environmentVariable("MODRINTH_TOKEN")
            .orElse(providers.gradleProperty("modrinth.token")))
        minecraftVersions.addAll(gameVersions)
        if (requires.isNotEmpty()) requires(*requires.toTypedArray())
        if (optional.isNotEmpty()) optional(*optional.toTypedArray())
    }

    if (setting("curseforgeProject").isNotEmpty()) curseforge {
        projectId.set(setting("curseforgeProject"))
        accessToken.set(providers.environmentVariable("CURSEFORGE_TOKEN")
            .orElse(providers.gradleProperty("curseforge.token")))
        minecraftVersions.addAll(gameVersions)
        javaVersions.add(JavaVersion.toVersion(property("java_version") as String))
        client.set(true)
        server.set(true)
        if (requires.isNotEmpty()) requires(*requires.toTypedArray())
        if (optional.isNotEmpty()) optional(*optional.toTypedArray())
    }
}

if (skipped) logger.info("publish: $path is left out (${if (unreleased) "unreleased Minecraft $minecraft" else "golem.build.publish.skip"})")
