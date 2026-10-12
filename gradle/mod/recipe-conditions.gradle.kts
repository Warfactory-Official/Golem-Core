import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// Shared by every Golem Core mod. Apply it from each loader buildscript:
//
//     apply(from = rootProject.file("../Golem-Core/gradle/mod/recipe-conditions.gradle.kts"))
//
// Parameters: none of its own. It reads the node facts the loader buildscript sets as extra properties before
// applying:
//   mc_version   the node's Minecraft version (required)
//   loader       "neoforge", "forge" or "fabric" (required)
//
// Every loader can skip a data file (a recipe, an advancement, a loot table) when a condition fails, and each spells
// it differently. A mod writes its conditions once, under the neutral key "golem:conditions", and this rewrites that
// key in processResources' output to the node's own spelling.
//
// Not for removing another mod's file by shipping a never-file at its path: which mod's pack wins a path is not
// dependable (Fabric API 1.20.1 orders mod packs as getAllMods() iterates, not by dependency; seen 2026-10-08). A
// machine table's rows are removed with RecipeTables' merged data/<own namespace>/recipe_removals lists instead.
//
// Neutral conditions (all of them must hold):
//   {"type": "never"}                          never loads
//   {"type": "mod_loaded", "mod": "<id>"}      loads when the mod is present
//   {"type": "not", "value": <condition>}      negation
//
// Spellings:
//   NeoForge 1.21.1, 26.1  "neoforge:conditions": [{"type": "neoforge:false"} (26.1: "neoforge:never")
//                          | {"type": "neoforge:mod_loaded", "modid"} | {"type": "neoforge:not", "value"}]
//   Forge 1.20.1           "conditions" in recipes, "forge:conditions" elsewhere: [{"type": "forge:false"}
//                          | {"type": "forge:mod_loaded", "modid"} | {"type": "forge:not", "value"}]
//   Fabric 1.21.1, 26.1    "fabric:load_conditions": [{"condition": "fabric:not", "value": {"condition": "fabric:true"}}
//                          | {"condition": "fabric:all_mods_loaded", "values": [id]} | {"condition": "fabric:not", "value"}]
//   Fabric 1.20.1          as above, but there is no fabric:true; never is {"condition": "fabric:any_mod_loaded", "values": []}

fun Project.applyRecipeConditions(mcVersion: String, loader: String) {
    val major = mcVersion.substringBefore('.').toIntOrNull() ?: 0
    val legacy = major < 26 && mcVersion.startsWith("1.20")

    fun convert(condition: Any?): Any? {
        @Suppress("UNCHECKED_CAST")
        val c = condition as? Map<String, Any?> ?: error("golem:conditions entry is not an object: $condition")
        return when (val type = c["type"]) {
            "never" -> when (loader) {
                "neoforge" -> mapOf("type" to if (major >= 26) "neoforge:never" else "neoforge:false")
                "forge" -> mapOf("type" to "forge:false")
                else -> if (legacy) mapOf("condition" to "fabric:any_mod_loaded", "values" to emptyList<String>())
                        else mapOf("condition" to "fabric:not", "value" to mapOf("condition" to "fabric:true"))
            }
            "mod_loaded" -> {
                val mod = c["mod"] ?: error("golem:conditions mod_loaded needs \"mod\"")
                when (loader) {
                    "neoforge" -> mapOf("type" to "neoforge:mod_loaded", "modid" to mod)
                    "forge" -> mapOf("type" to "forge:mod_loaded", "modid" to mod)
                    else -> mapOf("condition" to "fabric:all_mods_loaded", "values" to listOf(mod))
                }
            }
            "not" -> {
                val value = convert(c["value"])
                when (loader) {
                    "neoforge" -> mapOf("type" to "neoforge:not", "value" to value)
                    "forge" -> mapOf("type" to "forge:not", "value" to value)
                    else -> mapOf("condition" to "fabric:not", "value" to value)
                }
            }
            else -> error("unknown golem:conditions type $type")
        }
    }

    // golem.build.resourceTasks: the ProcessResources tasks to convert (default processResources), as in legacy-recipes
    val resourceTasks = (findProperty("golem.build.resourceTasks")?.toString() ?: "processResources").split(',').map { it.trim() }
    tasks.withType<ProcessResources>().matching { it.name in resourceTasks }.configureEach {
        doLast {
            var converted = 0
            destinationDir.walkTopDown()
                .filter { it.isFile && it.extension == "json" && it.invariantSeparatorsPath.contains("/data/") }
                .forEach { file ->
                    val text = file.readText()
                    if (!text.contains("\"golem:conditions\"")) return@forEach
                    @Suppress("UNCHECKED_CAST")
                    val json = JsonSlurper().parseText(text) as? MutableMap<String, Any?> ?: return@forEach
                    val neutral = json.remove("golem:conditions") as? List<*> ?: return@forEach
                    val conditions = neutral.map { convert(it) }
                    val isRecipe = Regex("/data/[^/]+/recipes?/").containsMatchIn(file.invariantSeparatorsPath)
                    val key = when (loader) {
                        "neoforge" -> "neoforge:conditions"
                        "forge" -> if (isRecipe) "conditions" else "forge:conditions"
                        else -> "fabric:load_conditions"
                    }
                    // a conditions key must come first so the loaders see it before the rest of the file
                    val out = LinkedHashMap<String, Any?>()
                    out[key] = conditions
                    out.putAll(json)
                    file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(out)))
                    converted++
                }
            logger.lifecycle("recipeConditions: converted $converted file(s) for $loader $mcVersion ($name)")
        }
    }
}

applyRecipeConditions(
    (findProperty("mc_version") ?: error("recipe-conditions needs extra[\"mc_version\"]")).toString(),
    (findProperty("loader") ?: error("recipe-conditions needs extra[\"loader\"]")).toString())
