import java.util.function.BiFunction

// Shared by every Golem Core mod built on Fabric. Apply it from the Fabric buildscript, before the dependencies block:
//
//     apply(from = rootProject.file("../Golem-Core/gradle/mod/composite-freshness.gradle.kts"))
//
// Parameters (project properties, set in gradle.properties or as extra[...] before the apply line):
//
//   golem.build.composite.roots   List of "<path>=<maven group path>" pairs, one per composite build this mod
//                                 compiles against, e.g. "../Golem-Core=dev/golem,../NTM-Continuum=com/hbm".
//                                 Default: "../Golem-Core=dev/golem".
//   golem.build.composite.artifactDirectory
//                                 Optional BiFunction<File, String, File>: upstream checkout and node name
//                                 (e.g. 1.21.1-fabric) -> its actual jar directory. Defaults to
//                                 <checkout>/versions/<node>/build/libs. Set before applying this script.
//   mc_version                    The node's Minecraft version (the Fabric buildscript sets it).
//
// Why. Loom caches its remap of a mod jar under .gradle/loom-cache/remapped_mods/remapped/<group>/<artifact>/<version>,
// keyed by the coordinates, never the content. A composite build's version string is constant, so an edited upstream
// produces a byte-different jar under an identical key and Loom keeps serving the old remap: the mod compiles against
// a stale upstream with no error. Only the obfuscated Fabric nodes (below 26) remap, so five nodes agree and the quiet
// ones are wrong. Two halves:
//
//   1. At configuration time, a cached remap older than any upstream input is deleted (Loom remaps again).
//   2. A task, checkCompositeJarFreshness, fails the build when an upstream's jar on disk is older than its sources:
//      Loom resolves the substituted dependency before the composite build's jar task could run, so a fresh remap of
//      a stale jar is just as stale. Nothing in the same invocation can repair that, so it stops and names the fix.
//      It is a task, not configuration-time code, so one stale node does not block the others, and not a doFirst on
//      compileJava, which an upstream-only edit leaves UP-TO-DATE.

val compositeMcVersion = (findProperty("mc_version") ?: error("composite-freshness needs extra[\"mc_version\"]")).toString()
val compositeObfuscated = compositeMcVersion.substringBefore('.').toInt() < 26

val compositeRoots: List<Pair<File, String>> = (findProperty("golem.build.composite.roots")?.toString()
    ?: "../Golem-Core=dev/golem").split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { entry ->
    val (path, group) = entry.split('=', limit = 2)
    rootProject.file(path.trim()) to group.trim()
}

val compositeArtifactResolver = findProperty("golem.build.composite.artifactDirectory")?.let { resolver ->
    require(resolver is BiFunction<*, *, *>) {
        "golem.build.composite.artifactDirectory must be a BiFunction<File, String, File>"
    }
    @Suppress("UNCHECKED_CAST")
    resolver as BiFunction<File, String, File>
}

/** The same artifact location feeds remap invalidation and the stale-jar check. */
fun compositeJarDirectory(root: File): File {
    val resolver = compositeArtifactResolver
    return if (resolver == null) root.resolve("versions/$compositeMcVersion-fabric/build/libs")
        else resolver.apply(root, "$compositeMcVersion-fabric")
}

fun compositeNewest(root: File): Long? = sequence {
    yieldAll(root.resolve("src").walkTopDown().filter { it.isFile || it.isDirectory })
    yield(root.resolve("build.fabric.gradle.kts"))
    yield(root.resolve("settings.gradle.kts"))
    yield(root.resolve("stonecutter.gradle.kts"))
    yield(root.resolve("gradle.properties"))
    yield(root.resolve("versions/$compositeMcVersion-fabric/gradle.properties"))
    yieldAll(compositeJarDirectory(root).listFiles()
        ?.filter { it.name.endsWith(".jar") && !it.name.endsWith("-sources.jar") }.orEmpty())
}.filter { it.isFile || it.isDirectory }.maxOfOrNull { it.lastModified() }

if (compositeObfuscated) {
    for ((root, group) in compositeRoots) {
        val cacheRoot = rootProject.file(".gradle/loom-cache/remapped_mods/remapped/$group")
        if (!cacheRoot.isDirectory) continue
        val upstream = compositeNewest(root) ?: continue
        // A composite project's remap is cached under its project name: <mc>-fabric-<version>.
        cacheRoot.listFiles { file -> file.isDirectory && file.name.startsWith("$compositeMcVersion-fabric-") }
            ?.forEach { entry ->
                val cachedAt = entry.walkTopDown().filter { it.isFile }.maxOfOrNull { it.lastModified() }
                if (cachedAt != null && upstream > cachedAt) {
                    logger.lifecycle("${root.name} changed since its cached remap; invalidating ${entry.name}.")
                    val cachePath = cacheRoot.toPath().toRealPath()
                    val entryPath = entry.toPath().toRealPath()
                    require(entryPath != cachePath && entryPath.startsWith(cachePath)) {
                        "Refusing to invalidate a remap outside $cacheRoot: $entryPath"
                    }
                    check(entry.deleteRecursively()) { "Could not invalidate stale remap $entry" }
                }
            }
    }
}

val checkCompositeJarFreshness = tasks.register("checkCompositeJarFreshness") {
    doLast {
        if (!compositeObfuscated) return@doLast
        for ((root, _) in compositeRoots) {
            val jar = compositeJarDirectory(root)
                .listFiles { file -> file.name.endsWith(".jar") && !file.name.endsWith("-sources.jar") }
                ?.maxByOrNull { it.lastModified() } ?: continue
            val newestSource = root.resolve("src").walkTopDown().filter { it.isFile || it.isDirectory }
                .maxOfOrNull { it.lastModified() } ?: continue
            if (newestSource > jar.lastModified()) {
                throw GradleException(
                    "${root.name}'s sources are newer than ${jar.name}, and Loom has already remapped that jar. " +
                        "This node would compile against a stale ${root.name}. Build it first, then run this again: " +
                        "(cd ${root.path} && sh gradlew :$compositeMcVersion-fabric:build)"
                )
            }
        }
    }
}
tasks.named("compileJava") { dependsOn(checkCompositeJarFreshness) }
