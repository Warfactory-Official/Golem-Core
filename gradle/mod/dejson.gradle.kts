// Shared by every Golem Core mod. Apply it from each loader buildscript, after compileJava and the
// runtimeClasspath configuration exist:
//
//     apply(from = rootProject.file("../Golem-Core/gradle/mod/dejson.gradle.kts"))
//
// Parameters. Every shared build-layer script under Golem-Core/gradle/mod reads its settings as project
// properties named `golem.build.<script>.<name>`, looked up with findProperty, so they can be set either
// as `extra["golem.build..."] = value` in the buildscript BEFORE the apply line or in gradle.properties.
// A file parameter accepts a File or a path relative to the root project; a list parameter accepts a
// Collection or a comma-separated String.
//
//   golem.build.dejson.classes        File or path of the declaration-class list: one fully qualified
//                                     class name per line, blank lines and `#` comments ignored. A
//                                     Collection of class names is accepted too.
//                                     Default: gradle/dejson-classes.txt
//   golem.build.dejson.authored       File or path of the authored-exception list (see dejsonOwnership
//                                     below). Default: gradle/dejson-authored.txt (optional file)
//   golem.build.dejson.unownedFolders List of resource-relative folder prefixes (ending in a slash)
//                                     whose JSON is not Dejson's, such as exported animation data that
//                                     merely lives under models. Default: none
//
// Block models, blockstates, item models, block loot tables and material tags declared in code are
// emitted as ordinary JSON here, at build time, and packed alongside the hand-written resources.
//
// Emitting rather than intercepting is deliberate. What ships is the same files the mod used to
// carry by hand, so every consumer of them — the model loader, resource packs, connected-texture
// engines, JEI, the model debugger — behaves exactly as before, and the only thing that changed is
// that nobody maintains them. The declaration vocabulary touches no Minecraft class, so this runs
// in a plain JVM with no game bootstrap.
//
// An authored file always wins: a path that exists under src/main/resources is left alone, and the
// generator defers to it. That is what makes adoption and retreat both one-step operations —
// deleting a JSON file hands it to the declaration, restoring it takes it back.

fun golemBuild(name: String): Any? = findProperty("golem.build.$name")
fun golemBuildList(name: String): List<String> = when (val value = golemBuild(name)) {
    null -> emptyList()
    is Collection<*> -> value.map { it.toString() }
    else -> value.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

fun golemBuildFile(name: String, default: String): File = when (val value = golemBuild(name)) {
    null -> rootProject.file(default)
    is File -> value
    else -> rootProject.file(value.toString())
}

// Every class holding declarations. Each is initialised in a plain JVM and then asked for its static
// declare() if it has one, so nothing any of them touches may need a running game. A registration class
// with no declare() records its declarations while its statics initialise (registration chains), and
// Minecraft is on this classpath but never bootstrapped. The order of the list is the initialisation order.
val DECLARATION_CLASSES: Array<String> = when (val value = golemBuild("dejson.classes")) {
    is Collection<*> -> value.map { it.toString() }
    else -> golemBuildFile("dejson.classes", "gradle/dejson-classes.txt").readLines()
        .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
}.toTypedArray()

val authoredResources = rootProject.file("src/main/resources")
val generatedResources = layout.buildDirectory.dir("generated/dejson")

// Compiled classes, not the source set's output: the output includes processResources, and this
// generator feeds processResources. Naming compileJava's directory keeps the two from depending on
// each other. The runtimeClasspath configuration is dependencies only, which is where Golem Core's
// dejson classes come from.
val compiledClasses = tasks.named<JavaCompile>("compileJava").flatMap { it.destinationDirectory }
val dejsonClasspath: FileCollection = files(compiledClasses, configurations["runtimeClasspath"])

// Verification is the whole point and runs first: a declaration is only safe to delete a file for
// once it has been held against that file and matched. Once the file is gone there is nothing left
// to check, so the check has to happen while both still exist.
val dejsonVerify = tasks.register<JavaExec>("dejsonVerify") {
    group = "verification"
    description = "Checks every code-declared model against the JSON file it stands in for."
    dependsOn(tasks.named("compileJava"))
    classpath = dejsonClasspath
    mainClass = "dev.golem.core.dejson.DejsonMain"
    args(
        "verify",
        generatedResources.get().asFile.absolutePath,
        authoredResources.absolutePath,
        *DECLARATION_CLASSES
    )
}

val dejsonGenerate = tasks.register<JavaExec>("dejsonGenerate") {
    group = "build"
    description = "Emits the code-declared models, blockstates and item definitions as JSON."
    dependsOn(tasks.named("compileJava"))
    inputs.dir(authoredResources)
    inputs.files(compiledClasses)
    outputs.dir(generatedResources)
    classpath = dejsonClasspath
    mainClass = "dev.golem.core.dejson.DejsonMain"
    args(
        "write",
        generatedResources.get().asFile.absolutePath,
        authoredResources.absolutePath,
        *DECLARATION_CLASSES
    )
}

tasks.named<org.gradle.language.jvm.tasks.ProcessResources>("processResources") {
    dependsOn(dejsonGenerate)
    from(generatedResources)
}

tasks.named("check") { dependsOn(dejsonVerify) }

// Emits every declaration regardless of what is authored, into a scratch directory that nothing
// packs. This is how you find out what a declaration actually produces without deleting its file
// first: generate everything, intersect with src/main/resources, compare, then delete the intersection.
tasks.register<JavaExec>("dejsonGenerateAll") {
    group = "verification"
    description = "Emits every code-declared file into build/generated/dejson-all, ignoring the authored resources."
    dependsOn(tasks.named("compileJava"))
    classpath = dejsonClasspath
    mainClass = "dev.golem.core.dejson.DejsonMain"
    args(
        "write", layout.buildDirectory.dir("generated/dejson-all").get().asFile.absolutePath,
        layout.buildDirectory.dir("generated/dejson-nothing").get().asFile.absolutePath,
        *DECLARATION_CLASSES
    )
}

// Every JSON file in these resource folders is a declaration's output. A hand-written file here is not an
// addition — it silently replaces whatever a declaration emits at that path ("file wins", above), which is
// how a mod's files can drift out of reach of dejsonVerify unnoticed. So an authored one fails the build
// instead, naming the declaration vocabulary to use. A mod whose generators also refuse to write into these
// folders should keep its generator list in step with this one. Exempt: the folders named by
// golem.build.dejson.unownedFolders (data that is not models at all), and anything listed in the authored
// manifest — which is the deliberate, reviewed way to take one file back from a declaration.
val DEJSON_OWNED_FOLDERS = listOf(
    "assets/*/items", "assets/*/models", "assets/*/blockstates", "assets/*/equipment", "assets/*/atlases",
    "data/*/tags", "data/*/loot_table", "data/*/recipe", "data/*/advancement", "data/*/damage_type",
    "data/*/worldgen", "data/*/data_maps", "data/*/jukebox_song", "data/*/neoforge/biome_modifier",
)
val DEJSON_UNOWNED_MODELS: List<String> = golemBuildList("dejson.unownedFolders")
// Dejson declares uniform boilerplate only (sprite items, cube_all-style parent+texture blocks, simple
// blockstates, loot, tags, recipes). Hand-tuned JSON — custom `elements` geometry, display transforms, one-off
// OBJ item definitions — stays an authored file, listed in the authored manifest: one resource-relative path
// per line, a trailing `/` exempts a whole folder. A mod's generators can read the same file to keep out of
// those paths. A listed path that no longer exists fails the check, so the list cannot rot.
val dejsonAuthoredManifest = golemBuildFile("dejson.authored", "gradle/dejson-authored.txt")
val dejsonAuthoredName = dejsonAuthoredManifest.relativeTo(rootProject.projectDir).invariantSeparatorsPath
val DEJSON_AUTHORED_EXCEPTIONS: Set<String> = if (!dejsonAuthoredManifest.isFile) emptySet() else
    dejsonAuthoredManifest.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()

val dejsonOwnership = tasks.register("dejsonOwnership") {
    group = "verification"
    description = "Fails when a JSON file is hand-written where Dejson declarations own the folder."
    inputs.dir(authoredResources)
    if (dejsonAuthoredManifest.isFile) inputs.file(dejsonAuthoredManifest)
    doLast {
        val root = authoredResources
        val stale = DEJSON_AUTHORED_EXCEPTIONS.filter { !File(root, it).exists() }.sorted()
        check(stale.isEmpty()) {
            "${stale.size} path(s) in $dejsonAuthoredName do not exist under src/main/resources:\n  " +
                    stale.joinToString("\n  ")
        }
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .filter { path ->
                DEJSON_AUTHORED_EXCEPTIONS.none { path == it || (it.endsWith("/") && path.startsWith(it)) } &&
                        DEJSON_UNOWNED_MODELS.none { path.startsWith(it) } &&
                        DEJSON_OWNED_FOLDERS.any { folder ->
                            val pattern = folder.split('/')
                            val parts = path.split('/')
                            parts.size > pattern.size && pattern.indices.all { pattern[it] == "*" || pattern[it] == parts[it] }
                        }
            }
            .sorted().toList()
        check(offenders.isEmpty()) {
            "${offenders.size} JSON file(s) are hand-written in folders Dejson owns; declare uniform boilerplate " +
                    "(Dejson.block/item/model, States, ItemModels, Tags, Drops, Equipment, ...), or list hand-tuned " +
                    "geometry/display transforms in $dejsonAuthoredName:\n  " + offenders.take(30)
                .joinToString("\n  ") +
                    (if (offenders.size > 30) "\n  ... and ${offenders.size - 30} more" else "")
        }
    }
}
dejsonGenerate.configure { dependsOn(dejsonOwnership) }
dejsonVerify.configure { dependsOn(dejsonOwnership) }
