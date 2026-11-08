// Data generated from Java (Golem Core DataSource / RecipeSource / DataGenerator: recipe tables, fluid properties, data
// maps, item pools, removal lists, ...), on every node: packs what the
// mod's generating run wrote into processResources' output, as ordinary datapack files that the other resource scripts
// then convert like any authored one. processResources depends on the run, so a build, a dev client or an IDE launch on
// any node generates the tables when they are missing or stale; nothing has to be run by hand first.
//
// Parameters (gradle.properties):
//   golem.build.generatedData.generator  The generating task (default ":26.1-neoforge:runGenerateData"). The mod's
//                                       26.1 NeoForge buildscript declares it: a gameTestServer run with
//                                       -Dgolem.data.output=<root> and -Dgolem.data.sources=<DataSources classes>.
//   golem.build.generatedData.root       The run's output root, relative to the root project (default
//                                       "build/generated/data").
//
// The positive signal is the manifest the generator writes beside the pack (`<namespace>/<folder> <files>` per line):
// after copying, every generated folder must hold exactly that many files (counted with its sub-folders). More means an
// authored file is left in a generated folder (it would be merged with or override the generated rows); fewer means rows
// never reached this node. Logs "generatedData: packed N generated file(s) in M folder(s)".

val generatedDataGenerator = findProperty("golem.build.generatedData.generator")?.toString() ?: ":26.1-neoforge:runGenerateData"
val generatedDataRoot = findProperty("golem.build.generatedData.root")?.toString()?.let {
    rootProject.layout.projectDirectory.dir(it)
} ?: rootProject.layout.buildDirectory.dir("generated/data").get()

// An authored file at a generated path would silently win over (or merge with) the generated one, so it fails the build,
// naming the file. While a folder is being migrated to Java (its authored files kept until the generated ones are proven
// equal), GOLEM_GENERATED_DATA_OVERLAP=authored lets the authored copy win, with a warning; only migration tooling sets it.
val generatedDataOverlapAllowed = System.getenv("GOLEM_GENERATED_DATA_OVERLAP") == "authored"
val generatedDataAuthored = rootProject.file("src/main/resources")

// A generated tag (Golem Core TagSource) may share its path with a tag the mod declares (Dejson) or authors: both are
// members of one tag, so the build writes their union. The union is staged here and wins the copy; the declared and
// authored copies at those paths are left out. A tag whose first Minecraft version (tags_since.txt) is newer than this
// node's mc_version is left out altogether.
val generatedTagsMerged = layout.buildDirectory.dir("generated/data-tags")
val generatedDataDejson = layout.buildDirectory.dir("generated/dejson")
val generatedDataMcVersion = findProperty("mc_version")?.toString()

fun generatedDataVersionAtLeast(version: String, first: String): Boolean {
    val have = version.split('.').map { it.toIntOrNull() ?: 0 }
    val want = first.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(have.size, want.size)) {
        val difference = have.getOrElse(i) { 0 } - want.getOrElse(i) { 0 }
        if (difference != 0) return difference > 0
    }
    return true
}

fun generatedTagsSince(): Map<String, String> {
    val file = generatedDataRoot.file("tags_since.txt").asFile
    if (!file.isFile) return emptyMap()
    return file.readLines().filter { it.isNotBlank() }.associate { line ->
        val (path, version) = line.trim().split(' ')
        "data/$path" to version
    }
}

/** Generated tag paths this node leaves out, relative to the pack root. */
fun generatedTagsLeftOut(): Set<String> = generatedTagsSince().filter { (_, first) ->
    generatedDataMcVersion != null && !generatedDataVersionAtLeast(generatedDataMcVersion, first)
}.keys

fun generatedTagValues(file: File): List<Any> {
    if (!file.isFile) return emptyList()
    @Suppress("UNCHECKED_CAST")
    val json = groovy.json.JsonSlurper().parse(file) as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    return (json["values"] as? List<Any>) ?: emptyList()
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(generatedDataGenerator)
    from(generatedDataRoot.dir("pack"))
    from(generatedTagsMerged)
    // the authored copy is first in the copy order, so EXCLUDE keeps it; doFirst has refused any overlap unless allowed
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val manifest = generatedDataRoot.file("manifest.txt")
    inputs.file(manifest).optional()
    inputs.file(generatedDataRoot.file("tags_since.txt")).optional()
    inputs.property("generatedDataOverlapAllowed", generatedDataOverlapAllowed)
    inputs.property("generatedDataMcVersion", generatedDataMcVersion ?: "")
    val merged = mutableSetOf<String>()
    val leftOut = mutableSetOf<String>()
    val staging = generatedTagsMerged.get().asFile
    eachFile {
        val path = relativePath.pathString
        if (path in leftOut && file.absolutePath.startsWith(generatedDataRoot.asFile.absolutePath)) exclude()
        else if (path in merged && !file.absolutePath.startsWith(staging.absolutePath)) exclude()
    }
    doFirst {
        val pack = generatedDataRoot.dir("pack").asFile
        val generated = pack.walkTopDown().filter { it.isFile }.map { it.relativeTo(pack).invariantSeparatorsPath }.toList()
        val isTag = Regex("^data/[^/]+/tags/.+")
        val overlap = generated.filter { !isTag.matches(it) && generatedDataAuthored.resolve(it).isFile }
        if (overlap.isNotEmpty()) {
            val list = overlap.take(20).joinToString("\n  ") + if (overlap.size > 20) "\n  ... (${overlap.size} in all)" else ""
            if (!generatedDataOverlapAllowed) {
                throw GradleException("generatedData: ${overlap.size} authored file(s) in src/main/resources are generated " +
                    "from Java; delete them (the source is the generator's):\n  $list")
            }
            logger.warn("generatedData: ${overlap.size} authored file(s) override generated ones (migration mode):\n  $list")
        }
        leftOut.clear()
        leftOut.addAll(generatedTagsLeftOut())
        merged.clear()
        staging.deleteRecursively()
        val dejson = generatedDataDejson.get().asFile
        for (path in generated.filter { isTag.matches(it) && it !in leftOut }) {
            val others = listOf(generatedDataAuthored.resolve(path), dejson.resolve(path)).filter { it.isFile }
            if (others.isEmpty()) continue
            val values = LinkedHashSet<Any>()
            others.forEach { values.addAll(generatedTagValues(it)) }
            values.addAll(generatedTagValues(pack.resolve(path)))
            val target = staging.resolve(path)
            target.parentFile.mkdirs()
            target.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(
                mapOf("replace" to false, "values" to values.toList()))) + "\n")
            merged.add(path)
        }
    }
    doLast {
        val skipped = leftOut.map { it.removePrefix("data/") }.toSet()
        val tables = manifest.asFile.readLines().filter { it.isNotBlank() }.map { line ->
            val (table, rows) = line.trim().split(' ')
            table to rows.toInt()
        }.filter { it.first !in skipped }
        val wrong = tables.mapNotNull { (key, rows) ->
            val (namespace, folder) = key.split('/', limit = 2)
            // Below 1.21 the recipe, loot table and tag folders are plural (Golem Core's legacy-recipes renames them in this
            // task's output).
            val plural = listOf("recipe/" to "recipes/", "loot_table/" to "loot_tables/", "tags/item/" to "tags/items/",
                "tags/block/" to "tags/blocks/").firstOrNull { folder.startsWith(it.first) }
            val folders = if (plural != null) listOf(folder, plural.second + folder.removePrefix(plural.first)) else listOf(folder)
            val packed = folders.sumOf { path ->
                destinationDir.resolve("data/$namespace/$path").walkTopDown().count { it.isFile && it.name.endsWith(".json") }
            }
            if (packed == rows) null else "$key: generated $rows, packed $packed"
        }
        check(wrong.isEmpty()) {
            "generatedData: the packed folders do not match the generator's manifest (an authored file left in a generated " +
                "folder, or a missing row):\n  " + wrong.joinToString("\n  ")
        }
        logger.lifecycle("generatedData: packed ${tables.sumOf { it.second }} generated file(s) in ${tables.size} folder(s)" +
            (if (merged.isEmpty()) "" else ", ${merged.size} tag(s) merged with declared ones") +
            (if (leftOut.isEmpty()) "" else ", ${leftOut.size} tag(s) newer than this node left out"))
    }
}
