// The uploaded jar: this node's jar without the test harness. Dev runs and the composite builds keep loading the full
// jar; only the copy in build/release, which publish.gradle.kts uploads, loses the harness classes, its mixin config and
// the lines that register them. Apply before publish.gradle.kts.

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

val harnessPackage = "dev/golem/core/harness/"
// Shipped: mods register queries into the registry, and input polls read the hooks.
val shipped = setOf("HarnessQueryRegistry", "HarnessHooks")
val harnessMixins = "golemcore.harness.mixins.json"
val harnessEntrypoint = "dev.golem.core.harness.HarnessFabricClient"

fun isShipped(path: String): Boolean {
    val rest = path.removePrefix(harnessPackage)
    return rest.isEmpty() || ('/' !in rest && rest.substringBefore('$').substringBefore(".class") in shipped)
}

fun dropped(path: String) = path == harnessMixins || (path.startsWith(harnessPackage) && !isShipped(path))

@Suppress("UNCHECKED_CAST")
fun withoutHarness(path: String, bytes: ByteArray): ByteArray {
    val text = bytes.toString(Charsets.UTF_8)
    val out = when (path) {
        "fabric.mod.json" -> {
            val json = JsonSlurper().parseText(text) as MutableMap<String, Any?>
            ((json["entrypoints"] as MutableMap<String, Any?>?)?.get("client") as MutableList<Any?>?)?.remove(harnessEntrypoint)
            (json["mixins"] as MutableList<Any?>?)?.remove(harnessMixins)
            JsonOutput.prettyPrint(JsonOutput.toJson(json))
        }
        "golemcore.refmap.json" -> {
            val json = JsonSlurper().parseText(text) as MutableMap<String, Any?>
            fun prune(table: Any?) = (table as MutableMap<String, Any?>?)?.keys?.removeIf { it.startsWith(harnessPackage) }
            prune(json["mappings"])
            (json["data"] as MutableMap<String, Any?>?)?.values?.forEach(::prune)
            JsonOutput.prettyPrint(JsonOutput.toJson(json))
        }
        "META-INF/neoforge.mods.toml" ->
            text.replace(Regex("\\[\\[mixins]]\\s*\\R\\s*config = \"${Regex.escape(harnessMixins)}\"\\s*\\R"), "")
        "META-INF/MANIFEST.MF" -> text.replace(",$harnessMixins", "")
        else -> return bytes
    }
    return out.toByteArray(Charsets.UTF_8)
}

// Every harness name left in a shipped class, other than the shipped ones; a non-empty answer means a dangling link.
fun danglingHarnessNames(bytes: ByteArray): Set<String> {
    val text = String(bytes, Charsets.ISO_8859_1)
    return Regex(Regex.escape(harnessPackage) + "[A-Za-z0-9_/$]+").findAll(text).map { it.value }
        .filterNot { isShipped(it + ".class") }.toSet()
}

val devJarTask = listOf("remapJar", "reobfJar").firstOrNull { it in tasks.names } ?: "jar"
val devJar = tasks.named<AbstractArchiveTask>(devJarTask).flatMap { it.archiveFile }
val releaseFile = layout.buildDirectory.zip(devJar) { dir, jar -> dir.file("release/${jar.asFile.name}") }

val releaseJar = tasks.register("releaseJar") {
    group = "build"
    description = "Copies this node's jar without the test harness, for upload."
    inputs.file(devJar)
    outputs.file(releaseFile)
    doLast {
        val source = devJar.get().asFile
        val target = releaseFile.get().asFile
        target.parentFile.mkdirs()
        var removed = 0
        val dangling = sortedSetOf<String>()
        ZipFile(source).use { zip ->
            ZipOutputStream(target.outputStream()).use { out ->
                for (entry in zip.entries()) {
                    if (dropped(entry.name)) { removed++; continue }
                    var bytes = zip.getInputStream(entry).use { it.readBytes() }
                    if (entry.name.endsWith(".class")) dangling += danglingHarnessNames(bytes)
                    else bytes = withoutHarness(entry.name, bytes)
                    out.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                    out.write(bytes)
                    out.closeEntry()
                }
            }
        }
        if (dangling.isNotEmpty()) throw GradleException("releaseJar: shipped classes still name harness classes: $dangling")
        ZipFile(target).use { zip ->
            for (name in listOf("fabric.mod.json", "META-INF/neoforge.mods.toml", "META-INF/MANIFEST.MF")) {
                val entry = zip.getEntry(name) ?: continue
                val text = zip.getInputStream(entry).use { it.readBytes() }.toString(Charsets.UTF_8)
                if (harnessMixins in text || harnessEntrypoint in text) {
                    throw GradleException("releaseJar: $name still registers the harness")
                }
            }
        }
        logger.lifecycle("releaseJar: dropped $removed harness file(s) from ${source.name}")
    }
}

tasks.named("assemble") { dependsOn(releaseJar) }
extra["golem.releaseJar"] = releaseJar.flatMap { layout.file(releaseFile.map { it.asFile }) }
