import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// Shared by every Golem Core mod. Apply it from each loader buildscript:
//
//     apply(from = rootProject.file("../Golem-Core/gradle/mod/legacy-assets.gradle.kts"))
//
// Parameters. Every shared build-layer script under Golem-Core/gradle/mod reads its settings as project
// properties named `golem.build.<script>.<name>`, looked up with findProperty, so they can be set either
// as `extra["golem.build..."] = value` in the buildscript BEFORE the apply line or in gradle.properties.
// A list parameter accepts a Collection or a comma-separated String.
//
//   golem.build.legacyAssets.modelNamespace          namespace of the custom item-model vocabulary the
//                                                    conversion understands: the `<ns>:machine_obj`,
//                                                    `<ns>:gun` and `<ns>:custom_item` item-model types and
//                                                    the `<ns>:item/<frame>_obj_context_legacy`,
//                                                    `<ns>:item/<frame>_obj_flat_legacy`,
//                                                    `<ns>:item/<frame>_sprite_legacy` and
//                                                    `<ns>:item/machine_obj_legacy` marker models that stand
//                                                    in for them below 1.21.4. Default: the `mod.id` property
//   golem.build.legacyAssets.selectProperties        `minecraft:select` properties that are registered as
//                                                    legacy float predicates under the same name, so a select
//                                                    over them becomes ordered model overrides. Default: none
//   golem.build.legacyAssets.selfRenderedSpecialTypes item-model types that, nested in `minecraft:special`,
//                                                    are drawn by the mod's own item renderer and so get the
//                                                    machine_obj_legacy marker. Default: `<ns>:machine_obj`
//
// Exported, for a mod's own resource checks applied after this script: the extra property
// `golem.legacyAssets.parentModelOf`, a `(File) -> String?` giving the model an item definition resolves
// to below 1.21.4 (the same function the conversion uses). It is set on every node.
//
// Node facts it also reads, which the loader buildscript sets as extra properties before applying:
//   mc_version  the node's Minecraft version (required)

fun golemBuild(name: String): Any? = findProperty("golem.build.$name")
fun golemBuildList(name: String): List<String>? = when (val value = golemBuild(name)) {
    null -> null
    is Collection<*> -> value.map { it.toString() }
    else -> value.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

val ns: String = golemBuild("legacyAssets.modelNamespace")?.toString() ?: findProperty("mod.id").toString()
val LEGACY_SELECT_PROPERTIES: Set<String> = golemBuildList("legacyAssets.selectProperties").orEmpty().toSet()
val SELF_RENDERED_SPECIAL_TYPES: Set<String> =
    (golemBuildList("legacyAssets.selfRenderedSpecialTypes") ?: listOf("$ns:machine_obj")).toSet()

/**
 * Converts client assets written in the 26.1 layout into the pre-1.21.4 layout.
 *
 * Two unrelated things changed between the eras, and both fail *silently* — the game logs a warning and
 * draws a missing-texture cube rather than erroring, so a build and a server boot both stay green:
 *
 *  1. **Item models moved a level of indirection.** From 1.21.4 an item is described by
 *     `assets/<ns>/items/<name>.json` (an "item definition": which model, plus conditions/overrides),
 *     and `models/item/<name>.json` is only needed when that definition points at one. Before that, the
 *     item model *is* `models/item/<name>.json` and the definition layer does not exist — so every item
 *     whose definition points straight at a block model (every block item) has no item model at
 *     all on the older line. This generates the missing ones from the definitions themselves.
 *
 *  2. **There was no `items` atlas.** Item textures are stitched into the *blocks* atlas until 1.21.4.
 *     The mod ships `assets/minecraft/atlases/items.json` to register its non-standard `textures/items/`
 *     directory; on the older line that file names an atlas that does not exist and is ignored, so hundreds of
 *     item textures never get stitched. Its sources are folded into `blocks.json` instead. (Atlas files
 *     from every pack are merged, not overridden, which is why the mod can ship a sources-only file.)
 *
 * Like the recipe conversion, this rewrites `processResources`' OUTPUT: `src/` keeps exactly one copy of
 * every asset, in the 26.1 shape, and nothing can leak between nodes.
 */
/**
 * The item model every OBJ machine gets below 1.21.4. Its `builtin/entity` parent is what makes vanilla
 * report `isCustomRenderer()` and route the item to the loader's BEWLR / DynamicItemRenderer.
 */
val MACHINE_OBJ_LEGACY_MODEL = "$ns:item/machine_obj_legacy"

/**
 * Marker for context-aware machine OBJ definitions. Its display block is the exact perspective matrix
 * the mod's legacy item renderer expects.
 */
val MACHINE_OBJ_CONTEXT_LEGACY_MODEL = "$ns:item/machine_obj_context_legacy"

/** The same marker for the missile frame. */
val MISSILE_OBJ_CONTEXT_LEGACY_MODEL = "$ns:item/missile_obj_context_legacy"

fun Project.applyLegacyItemAssets() {
    tasks.named<ProcessResources>("processResources").configure {
        doLast {
            val root = destinationDir
            val assets = File(root, "assets")
            if (!assets.isDirectory) return@doLast

            var generated = 0
            assets.listFiles()?.filter { it.isDirectory }?.forEach { namespace ->
                val definitions = File(namespace, "items")
                if (!definitions.isDirectory) return@forEach
                val itemModels = File(namespace, "models/item")

                definitions.listFiles()?.filter { it.extension == "json" }?.forEach { definition ->
                    val target = File(itemModels, definition.name)
                    val parent = parentModelOf(definition) ?: return@forEach
                    // A model that is its own definition's target. The two machine_obj frame markers are
                    // declared through the item vocabulary, so each gets an item definition naming itself;
                    // writing that parent out would replace the model with one inheriting from itself and
                    // drop the display block it exists to carry.
                    if (parent == "${namespace.name}:item/${definition.nameWithoutExtension}") return@forEach
                    // A self-rendered item's model MUST be the builtin/entity marker, so it overwrites
                    // any existing file: a couple of ducts still carry a flat sprite model that 26.1 no
                    // longer references (their definitions point at the OBJ renderer), and leaving it in
                    // place would render them flat here and 3D there. The same applies to a gun, whose
                    // placeholder sprite predates its OBJ renderer. Every other item defers to what ships.
                    val selfRendered = parent == MACHINE_OBJ_LEGACY_MODEL || parent.endsWith("_obj_context_legacy")
                            || parent.endsWith("_obj_flat_legacy") || parent.endsWith("_sprite_legacy")
                            || isGunDefinition(definition)
                    if (target.exists() && !selfRendered) return@forEach
                    itemModels.mkdirs()
                    // A sprite the renderer draws on wears the sprite's own frame (26.1 poses it in the flat model's
                    // display): keep the sprite model's display{} over the shared marker's (hand poses a sprite
                    // model states for itself).
                    @Suppress("UNCHECKED_CAST")
                    val ownDisplay = if (parent.endsWith("_sprite_legacy") && target.isFile)
                        (JsonSlurper().parse(target) as? Map<String, Any?>)?.get("display") else null
                    val marker = if (ownDisplay != null) mapOf("parent" to parent, "display" to ownDisplay) else mapOf("parent" to parent)
                    target.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(marker)))
                    generated++
                }
            }

            // Model switching on a custom_model_data condition. 26.1 picks between two models in the item
            // definition; below 1.21.4 the same choice is a model `overrides` predicate on the losing
            // model. The on_false model IS an existing file (that is what the definition points at), so
            // this adds the override to it rather than generating a replacement.
            var conditioned = 0
            assets.listFiles()?.filter { it.isDirectory }?.forEach { namespace ->
                val definitions = File(namespace, "items")
                if (!definitions.isDirectory) return@forEach
                definitions.listFiles()?.filter { it.extension == "json" }?.forEach { definition ->
                    val branches = customModelDataConditionOf(definition) ?: return@forEach
                    val (whenFalse, whenTrue) = branches
                    val target = File(namespace, "models/item/" + whenFalse.substringAfter("item/") + ".json")
                    if (!target.isFile) return@forEach
                    @Suppress("UNCHECKED_CAST")
                    val model = JsonSlurper().parse(target) as MutableMap<String, Any?>
                    model["overrides"] = listOf(mapOf("predicate" to mapOf("custom_model_data" to 1), "model" to whenTrue))
                    target.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(model)))
                    conditioned++
                }
            }

            // Items whose definition selects on one of the configured select properties (golem.build.legacyAssets.selectProperties):
            // the modern item definition selects its visual-state models; older clients express the same selection as ordered overrides.
            var selected = 0
            assets.listFiles()?.filter { it.isDirectory }?.forEach { namespace ->
                val definitions = File(namespace, "items")
                if (!definitions.isDirectory) return@forEach
                definitions.listFiles()?.filter { it.extension == "json" }?.forEach { definition ->
                    val branches = itemVariantSelectOf(definition) ?: return@forEach
                    val (property, cases) = branches
                    // The item's OWN model, not the fallback's: overrides are not inherited through
                    // `parent`, so they have to sit on the file vanilla resolves for the item itself.
                    val target = File(namespace, "models/item/" + definition.nameWithoutExtension + ".json")
                    if (!target.isFile) return@forEach
                    @Suppress("UNCHECKED_CAST")
                    val model = JsonSlurper().parse(target) as MutableMap<String, Any?>
                    // Ascending: vanilla scans overrides from the end and takes the first whose
                    // threshold the value clears, so a case out of order would shadow the ones after it.
                    model["overrides"] = cases.sortedBy { it.first }.map { (value, selectedModel) ->
                        mapOf("predicate" to mapOf(property to value), "model" to selectedModel)
                    }
                    target.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(model)))
                    selected++
                }
            }

            val atlases = File(assets, "minecraft/atlases")
            val items = File(atlases, "items.json")
            val blocks = File(atlases, "blocks.json")
            var folded = 0
            if (items.isFile && blocks.isFile) {
                @Suppress("UNCHECKED_CAST")
                val itemSources = (JsonSlurper().parse(items) as Map<String, Any?>)["sources"] as? List<Any?>
                @Suppress("UNCHECKED_CAST")
                val blockJson = JsonSlurper().parse(blocks) as MutableMap<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val blockSources = (blockJson["sources"] as? List<Any?>).orEmpty()
                if (itemSources != null) {
                    blockJson["sources"] = blockSources + itemSources.filter { it !in blockSources }
                    blocks.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(blockJson)))
                    items.delete()
                    folded = itemSources.size
                }
            }

            // Worn armor textures. 26.1 names them through an equipment asset (`equipment/<material>.json`,
            // whose layers point at `textures/entity/equipment/humanoid[_leggings]/<tex>.png`); 1.21.1 and
            // 1.20.1 look for `textures/models/armor/<material>_layer_1/2.png` by the material name alone,
            // and a missing one draws solid magenta. An authored layer file wins.
            var armorLayers = 0
            assets.listFiles()?.filter { it.isDirectory }?.forEach { namespace ->
                val equipment = File(namespace, "equipment")
                if (!equipment.isDirectory) return@forEach
                equipment.listFiles()?.filter { it.extension == "json" }?.forEach { asset ->
                    @Suppress("UNCHECKED_CAST")
                    val layers = (JsonSlurper().parse(asset) as Map<String, Any?>)["layers"] as? Map<String, Any?>
                        ?: return@forEach
                    for ((kind, index) in listOf("humanoid" to 1, "humanoid_leggings" to 2)) {
                        @Suppress("UNCHECKED_CAST")
                        val texture = ((layers[kind] as? List<Any?>)?.firstOrNull() as? Map<String, Any?>)
                            ?.get("texture") as? String ?: continue
                        val texNamespace = texture.substringBefore(':', "minecraft")
                        val source = File(assets, "$texNamespace/textures/entity/equipment/$kind/" +
                            texture.substringAfter(':') + ".png")
                        val target = File(namespace, "textures/models/armor/${asset.nameWithoutExtension}_layer_$index.png")
                        if (!source.isFile || target.exists()) continue
                        target.parentFile.mkdirs()
                        source.copyTo(target)
                        armorLayers++
                    }
                }
            }

            logger.lifecycle(
                "legacyItemAssets: generated $generated item model(s); rewrote $conditioned conditional model(s); selected $selected item-variant model(s); " +
                    "folded $folded item atlas source(s) into blocks.json; copied $armorLayers armor layer texture(s).")
        }
    }
}

/**
 * The model an item definition resolves to, as a plain parent reference, or null if this definition has
 * no single static model to fall back on.
 *
 * `minecraft:model` is the ordinary case and names its model directly.
 *
 * `minecraft:special` pairs a base model with a special renderer; the `<ns>:machine_obj` item-model type
 * is nested under the shared special-model base. For the mod's OBJ machine renderer the
 * older-version equivalent is a per-loader "render this item yourself" hook (NeoForge's BEWLR, Fabric's
 * DynamicItemRenderer) — and vanilla only consults that hook when the baked model reports
 * `isCustomRenderer()`, which is true exactly when the model's parent chain ends at
 * `minecraft:builtin/entity`. So these get `<ns>:item/machine_obj_legacy`, which does that and carries the
 * block display transforms. Any other special renderer falls back to its own `base`, which is precisely
 * what vanilla draws underneath it.
 */
/**
 * For an item definition that selects a model on a `minecraft:custom_model_data` condition, the pair
 * (model when false, model when true); null for anything else. Only the boolean-flag form is handled,
 * which is the one `ModelData.setColorAndFlag` produces.
 */
fun customModelDataConditionOf(definition: File): Pair<String, String>? {
    @Suppress("UNCHECKED_CAST")
    val json = JsonSlurper().parse(definition) as? Map<String, Any?> ?: return null
    @Suppress("UNCHECKED_CAST")
    val model = json["model"] as? Map<String, Any?> ?: return null
    if (model["type"] != "minecraft:condition" || model["property"] != "minecraft:custom_model_data") return null
    @Suppress("UNCHECKED_CAST")
    val onTrue = (model["on_true"] as? Map<String, Any?>)?.get("model") as? String ?: return null
    @Suppress("UNCHECKED_CAST")
    val onFalse = (model["on_false"] as? Map<String, Any?>)?.get("model") as? String ?: return null
    return onFalse to onTrue
}

fun itemVariantSelectOf(definition: File): Pair<String, List<Pair<Float, String>>>? {
    @Suppress("UNCHECKED_CAST")
    val json = JsonSlurper().parse(definition) as? Map<String, Any?> ?: return null
    @Suppress("UNCHECKED_CAST")
    val model = json["model"] as? Map<String, Any?> ?: return null
    // Each configured property may be a component or a client-state select; Core's ItemModelProperties
    // installs them as legacy float predicates under the same name.
    val property = model["property"] as? String ?: return null
    if (model["type"] != "minecraft:select" || property !in LEGACY_SELECT_PROPERTIES) return null
    @Suppress("UNCHECKED_CAST")
    (model["fallback"] as? Map<String, Any?>)?.get("model") as? String ?: return null
    @Suppress("UNCHECKED_CAST")
    val cases = model["cases"] as? List<Map<String, Any?>> ?: return null
    val parsed = cases.mapNotNull { entry ->
        val value = (entry["when"] as? String)?.toFloatOrNull() ?: return@mapNotNull null
        @Suppress("UNCHECKED_CAST")
        val selected = (entry["model"] as? Map<String, Any?>)?.get("model") as? String ?: return@mapNotNull null
        value to selected
    }
    return property to (parsed.takeIf { it.isNotEmpty() } ?: return null)
}

fun isMachineObjDirect(model: Any?): Boolean {
    @Suppress("UNCHECKED_CAST")
    val json = model as? Map<String, Any?> ?: return false
    return json["type"] == "$ns:machine_obj"
}

fun isMachineObjSpecial(model: Any?): Boolean {
    @Suppress("UNCHECKED_CAST")
    val json = model as? Map<String, Any?> ?: return false
    if (json["type"] != "minecraft:special") return false
    @Suppress("UNCHECKED_CAST")
    val inner = json["model"] as? Map<String, Any?> ?: return false
    return inner["type"] == "$ns:machine_obj"
}

fun isGunDefinition(definition: File): Boolean {
    @Suppress("UNCHECKED_CAST")
    val root = JsonSlurper().parse(definition) as? Map<String, Any?> ?: return false
    @Suppress("UNCHECKED_CAST")
    val model = root["model"] as? Map<String, Any?> ?: return false
    return model["type"] == "$ns:gun"
}

/**
 * Which shared frame a `<ns>:machine_obj` model wears below 1.21.4. The `frame` field names the legacy item
 * render class; absent, it is `ItemRenderBase` like every machine.
 */
// Every frame the mod declares must ship its <frame>_obj_context_legacy marker (machine and missile have named constants above).
fun contextLegacyModelOf(model: Map<String, Any?>): String =
    when (val frame = model["frame"] as? String) {
        null, "machine" -> MACHINE_OBJ_CONTEXT_LEGACY_MODEL
        "missile" -> MISSILE_OBJ_CONTEXT_LEGACY_MODEL
        else -> "$ns:item/${frame}_obj_context_legacy"
    }

fun parentModelOf(definition: File): String? {
    @Suppress("UNCHECKED_CAST")
    val json = JsonSlurper().parse(definition) as? Map<String, Any?> ?: return null
    @Suppress("UNCHECKED_CAST")
    val model = json["model"] as? Map<String, Any?> ?: return null
    return when (model["type"]) {
        "minecraft:model" -> model["model"] as? String
        "$ns:machine_obj" -> contextLegacyModelOf(model)
        // Gun bases inherit builtin/entity themselves, preserving their exact modern display matrices.
        "$ns:gun" -> model["base"] as? String
        // A ported legacy item renderer draws every slot from the BEWLR hook in its frame's display; a renderer that
        // leaves the inventory to the sprite wears the same frame with the sprite's front GUI light.
        "$ns:custom_item" -> {
            // generated/handheld: a sprite the renderer only adds to, drawn by the hook in the sprite's own frame
            val frame = model["frame"] as? String ?: "weapon"
            if (frame == "generated" || frame == "handheld") "$ns:item/${frame}_sprite_legacy"
            else if (model["flat"] != null) "$ns:item/${frame}_obj_flat_legacy" else "$ns:item/${frame}_obj_context_legacy"
        }
        "minecraft:special" -> {
            @Suppress("UNCHECKED_CAST")
            val inner = model["model"] as? Map<String, Any?>
             if ((inner?.get("type") as? String) in SELF_RENDERED_SPECIAL_TYPES) MACHINE_OBJ_LEGACY_MODEL
             else model["base"] as? String
         }
        "minecraft:select" -> {
            @Suppress("UNCHECKED_CAST")
            val fallback = model["fallback"]
            @Suppress("UNCHECKED_CAST")
            val cases = model["cases"] as? List<Map<String, Any?>> ?: emptyList()
            if (isMachineObjDirect(fallback) || cases.any { isMachineObjDirect(it["model"]) }) {
                // A variant select whose branches are context-aware machine_obj models: same frame the
                // unwrapped form gets, or the item resolves no model at all below 1.21.4.
                @Suppress("UNCHECKED_CAST")
                val direct = (if (isMachineObjDirect(fallback)) fallback
                        else cases.first { isMachineObjDirect(it["model"]) }["model"]) as Map<String, Any?>
                contextLegacyModelOf(direct)
            } else if (isMachineObjSpecial(fallback) || cases.any { isMachineObjSpecial(it["model"]) }) {
                MACHINE_OBJ_LEGACY_MODEL
            } else if ((fallback as? Map<String, Any?>)?.get("type") == "$ns:gun") {
                // A gun whose slot icon is a sprite and whose model is only drawn in hand: below 1.21.4
                // nothing reads the definition, so the item resolves models/item/<id>.json. Point it at
                // the sprite branch, which is what the slot shows on legacy versions too.
                @Suppress("UNCHECKED_CAST")
                val cases2 = model["cases"] as? List<Map<String, Any?>> ?: emptyList()
                ((cases2.firstOrNull()?.get("model") as? Map<String, Any?>)?.get("model") as? String)
            } else {
                // A select over ordinary sprite models still needs a model at the ITEM's own path.
                // Nothing below 1.21.4 reads the definition, so the item resolves
                // models/item/<id>.json and finds nothing at all — which is a missing texture, not a
                // wrong variant. Parent it to the branch the definition falls back to; the overrides
                // that pick the other branches are added below.
                @Suppress("UNCHECKED_CAST")
                ((fallback as? Map<String, Any?>)?.get("model") as? String)
            }
        }
        else -> null
    }
}

// Same gate as the recipe conversion: the item-definition layer and the items atlas both arrived in
// 1.21.4, one release after the ingredient change, but no node sits between the two — 1.21.1 and 1.20.1
// need all of it, 26.1 needs none of it. Kept as its own predicate so that stays visible if one ever does.
fun needsLegacyItemAssets(mcVersion: String): Boolean {
    val parts = mcVersion.split('.').map { it.toIntOrNull() ?: 0 }
    val major = parts.getOrElse(0) { 0 }
    val minor = parts.getOrElse(1) { 0 }
    val patch = parts.getOrElse(2) { 0 }
    return when {
        major >= 26 -> false
        minor > 21 -> false
        minor < 21 -> true
        else -> patch < 4
    }
}

if (needsLegacyItemAssets(extra.properties["mc_version"] as String)) {
    applyLegacyItemAssets()
}

// Exported for the mod's own resource checks (see the parameter block at the top).
extra["golem.legacyAssets.parentModelOf"] = { definition: File -> parentModelOf(definition) }
