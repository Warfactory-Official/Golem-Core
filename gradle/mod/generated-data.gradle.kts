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
val generatedDataRoot = rootProject.layout.projectDirectory.dir(
    findProperty("golem.build.generatedData.root")?.toString() ?: "build/generated/data")

// An authored file at a generated path would silently win over (or merge with) the generated one, so it fails the build,
// naming the file. While a folder is being migrated to Java (its authored files kept until the generated ones are proven
// equal), GOLEM_GENERATED_DATA_OVERLAP=authored lets the authored copy win, with a warning; only migration tooling sets it.
val generatedDataOverlapAllowed = System.getenv("GOLEM_GENERATED_DATA_OVERLAP") == "authored"
val generatedDataAuthored = rootProject.file("src/main/resources")

tasks.named<ProcessResources>("processResources") {
    dependsOn(generatedDataGenerator)
    from(generatedDataRoot.dir("pack"))
    // the authored copy is first in the copy order, so EXCLUDE keeps it; doFirst has refused any overlap unless allowed
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val manifest = generatedDataRoot.file("manifest.txt")
    inputs.file(manifest).optional()
    inputs.property("generatedDataOverlapAllowed", generatedDataOverlapAllowed)
    doFirst {
        val pack = generatedDataRoot.dir("pack").asFile
        val overlap = pack.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(pack).invariantSeparatorsPath }
            .filter { generatedDataAuthored.resolve(it).isFile }.toList()
        if (overlap.isNotEmpty()) {
            val list = overlap.take(20).joinToString("\n  ") + if (overlap.size > 20) "\n  ... (${overlap.size} in all)" else ""
            if (!generatedDataOverlapAllowed) {
                throw GradleException("generatedData: ${overlap.size} authored file(s) in src/main/resources are generated " +
                    "from Java; delete them (the source is the generator's):\n  $list")
            }
            logger.warn("generatedData: ${overlap.size} authored file(s) override generated ones (migration mode):\n  $list")
        }
    }
    doLast {
        val tables = manifest.asFile.readLines().filter { it.isNotBlank() }.map { line ->
            val (table, rows) = line.trim().split(' ')
            table to rows.toInt()
        }
        val wrong = tables.mapNotNull { (key, rows) ->
            val (namespace, folder) = key.split('/', limit = 2)
            // Below 1.21 the recipe and loot table folders are plural (Golem Core's legacy-recipes renames them in this task's output).
            val plural = listOf("recipe/" to "recipes/", "loot_table/" to "loot_tables/").firstOrNull { folder.startsWith(it.first) }
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
        logger.lifecycle("generatedData: packed ${tables.sumOf { it.second }} generated file(s) in ${tables.size} folder(s)")
    }
}
