// The uploaded jar: this node's jar without the GameTests, the harness queries and their test-only resources. Dev runs
// keep loading the full jar; only the copy in build/release, which publish.gradle.kts uploads, loses them. Shipped code
// never names a stripped class: the registrations sit in the stripped classes themselves, found by the loaders'
// annotation scan or by an entrypoint line this script removes from fabric.mod.json. The task fails if any shipped
// class still names one. Apply before publish.gradle.kts.
//
// Parameters (gradle.properties):
//   golem.build.releaseJar.testPackages   Class folders to drop, comma-separated (e.g. com/hbm/gametest/).
//   golem.build.releaseJar.testResources  Resource folders to drop, comma-separated (optional).
//   golem.build.releaseJar.testAssets     Asset file-name prefixes registered only by a GameTest (optional).
// Classes named *GameTest* or *HarnessQueries* are dropped wherever they are.

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

fun listProperty(name: String) = findProperty(name)?.toString()?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
    ?: emptyList()

val testPackages = listProperty("golem.build.releaseJar.testPackages")
val testResources = listProperty("golem.build.releaseJar.testResources")
val testAssetNames = listProperty("golem.build.releaseJar.testAssets")
if (testPackages.isEmpty()) throw GradleException("release-jar.gradle.kts: set golem.build.releaseJar.testPackages")

fun isDevClass(path: String): Boolean {
    if (!path.endsWith(".class")) return false
    val simple = path.substringAfterLast('/')
    return testPackages.any(path::startsWith) || "GameTest" in simple || "HarnessQueries" in simple
}

fun dropped(path: String) = testPackages.any(path::startsWith) || isDevClass(path) || testResources.any(path::startsWith) ||
    (path.startsWith("assets/") && testAssetNames.any { path.substringAfterLast('/').startsWith(it) })

fun className(entryName: String) = entryName.removeSuffix(".class").replace('/', '.')

@Suppress("UNCHECKED_CAST")
fun withoutDev(path: String, bytes: ByteArray, droppedClasses: Set<String>): ByteArray {
    if (path != "fabric.mod.json") return bytes
    val json = JsonSlurper().parseText(bytes.toString(Charsets.UTF_8)) as MutableMap<String, Any?>
    val entrypoints = json["entrypoints"] as MutableMap<String, MutableList<Any?>>?
    entrypoints?.values?.forEach { list ->
        list.removeIf { entry ->
            val value = if (entry is Map<*, *>) entry["value"] as String else entry as String
            value in droppedClasses
        }
    }
    entrypoints?.values?.removeIf { it.isEmpty() }
    return JsonOutput.prettyPrint(JsonOutput.toJson(json)).toByteArray(Charsets.UTF_8)
}

val devNameToken = Regex("[A-Za-z0-9_/$]*(?:" + (testPackages.map(Regex::escape) + listOf("GameTest", "HarnessQueries"))
    .joinToString("|") + ")[A-Za-z0-9_/$]*")

fun stripTo(source: File, target: File) {
    target.parentFile.mkdirs()
    val droppedNames = ZipFile(source).use { zip -> zip.entries().asSequence().map { it.name }.filter(::dropped).toSet() }
    val droppedClasses = droppedNames.filter { it.endsWith(".class") }.map(::className).toSet()
    val droppedInternal = droppedClasses.map { it.replace('.', '/') }.toSet()
    val dangling = sortedMapOf<String, MutableSet<String>>()
    ZipFile(source).use { zip ->
        ZipOutputStream(target.outputStream()).use { out ->
            for (entry in zip.entries()) {
                if (entry.name in droppedNames) continue
                var bytes = zip.getInputStream(entry).use { it.readBytes() }
                if (entry.name.endsWith(".class")) {
                    val text = String(bytes, Charsets.ISO_8859_1)
                    for (match in devNameToken.findAll(text)) {
                        val token = match.value.removePrefix("L")
                        if (token in droppedInternal || token.substringBefore('$') in droppedInternal) {
                            dangling.getOrPut(entry.name) { sortedSetOf() } += token
                        }
                    }
                } else {
                    bytes = withoutDev(entry.name, bytes, droppedClasses)
                }
                out.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                out.write(bytes)
                out.closeEntry()
            }
        }
    }
    if (dangling.isNotEmpty()) {
        throw GradleException("releaseJar: shipped classes still name stripped classes: " +
            dangling.entries.take(20).joinToString { "${it.key} -> ${it.value.take(3)}" })
    }
    ZipFile(target).use { zip ->
        val json = zip.getEntry("fabric.mod.json")?.let { zip.getInputStream(it).use { s -> s.readBytes() }.toString(Charsets.UTF_8) }
        if (json != null && droppedClasses.any { it in json }) throw GradleException("releaseJar: fabric.mod.json still names a stripped class")
    }
    logger.lifecycle("releaseJar: dropped ${droppedNames.size} file(s) from ${source.name}")
}

val devJarTask = listOf("remapJar", "reobfJar").firstOrNull { it in tasks.names } ?: "jar"
val devJar = tasks.named<AbstractArchiveTask>(devJarTask).flatMap { it.archiveFile }
val releaseFile = layout.buildDirectory.zip(devJar) { dir, jar -> dir.file("release/${jar.asFile.name}") }

val releaseJar = tasks.register("releaseJar") {
    group = "build"
    description = "Copies this node's jar without the GameTests and harness queries, for upload."
    inputs.file(devJar)
    outputs.file(releaseFile)
    doLast { stripTo(devJar.get().asFile, releaseFile.get().asFile) }
}

// The same strip applied to the unremapped jar, for booting the release content in a dev run (an obfuscated node's
// release jar is in the loader's production names, which a dev run cannot load).
val plainJar = tasks.named<AbstractArchiveTask>("jar").flatMap { it.archiveFile }
tasks.register("releaseDevJar") {
    group = "build"
    description = "Strips the unremapped jar into build/release-dev, to unpack over a dev run's classes."
    inputs.file(plainJar)
    val target = layout.buildDirectory.file("release-dev/stripped.jar")
    outputs.file(target)
    doLast { stripTo(plainJar.get().asFile, target.get().asFile) }
}

tasks.named("assemble") { dependsOn(releaseJar) }
extra["golem.releaseJar"] = releaseJar.flatMap { layout.file(releaseFile.map { it.asFile }) }
// The upload reads the stripped copy, so it must be built first.
tasks.matching { it.name in setOf("publishMods", "publishModrinth", "publishCurseforge") }.configureEach { dependsOn(releaseJar) }
