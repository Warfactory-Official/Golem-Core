import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// Shared by every Golem Core mod. Apply it from each loader buildscript:
//
//     apply(from = rootProject.file("../Golem-Core/gradle/mod/legacy-dimensions.gradle.kts"))
//
// Parameters. Every shared build-layer script under Golem-Core/gradle/mod reads its settings as project
// properties named `golem.build.<script>.<name>`; this one has none of its own. It reads the node fact the loader
// buildscript sets as an extra property before applying:
//   mc_version   the node's Minecraft version (required)
//
// A mod authors its dimension types once, in the 26.1 shape (environment attributes, a skybox, timelines and a
// default clock). Below 26.1 the codec is the older flat one: ultrawarm, natural, piglin_safe, bed_works,
// respawn_anchor_works, has_raids and an effects id instead of attributes and a skybox. This rewrites every
// data/<ns>/dimension_type file in processResources' output to that shape and logs how many it converted.
//
// Mapping (26.1 attribute or field -> legacy field, legacy default when absent):
//   skybox "overworld" | "end" | "none"                -> effects minecraft:overworld | minecraft:the_end | minecraft:the_nether
//   minecraft:gameplay/water_evaporates                -> ultrawarm (false)
//   minecraft:gameplay/bed_rule can_sleep != "never"   -> bed_works (true)
//   minecraft:gameplay/respawn_anchor_works            -> respawn_anchor_works (false)
//   minecraft:gameplay/piglins_zombify (negated)       -> piglin_safe (false)
//   minecraft:gameplay/can_start_raid                  -> has_raids (true)
//   minecraft:gameplay/nether_portal_spawns_piglin     -> natural, together with has_skylight (true)
//   has_fixed_time true                                -> fixed_time, from golem:fixed_time (default 6000)
// Removed: attributes, skybox, timelines, default_clock, has_fixed_time, has_ender_dragon_fight.
// A dimension type may carry "golem:effects" to name the legacy effects id explicitly (a mod's own).

fun Project.applyLegacyDimensionCompatibility(mcVersion: String) {
    if ((mcVersion.substringBefore('.').toIntOrNull() ?: 0) >= 26) return
    tasks.named<ProcessResources>("processResources").configure {
        doLast {
            var converted = 0
            destinationDir.walkTopDown()
                .filter { it.isFile && it.extension == "json" && it.invariantSeparatorsPath.contains("/dimension_type/") }
                .forEach { file ->
                    @Suppress("UNCHECKED_CAST")
                    val json = JsonSlurper().parse(file) as? MutableMap<String, Any?> ?: return@forEach
                    if (!json.containsKey("skybox") && !json.containsKey("attributes")) return@forEach
                    @Suppress("UNCHECKED_CAST")
                    val attributes = (json.remove("attributes") as? Map<String, Any?>) ?: emptyMap()
                    val skybox = json.remove("skybox") as? String
                    val explicitEffects = json.remove("golem:effects") as? String
                    json["effects"] = explicitEffects ?: when (skybox) {
                        "end" -> "minecraft:the_end"
                        "none" -> "minecraft:the_nether"
                        else -> "minecraft:overworld"
                    }
                    json["ultrawarm"] = attributes["minecraft:gameplay/water_evaporates"] as? Boolean ?: false
                    @Suppress("UNCHECKED_CAST")
                    val bedRule = attributes["minecraft:gameplay/bed_rule"] as? Map<String, Any?>
                    json["bed_works"] = bedRule?.get("can_sleep")?.let { it != "never" } ?: true
                    json["respawn_anchor_works"] = attributes["minecraft:gameplay/respawn_anchor_works"] as? Boolean ?: false
                    json["piglin_safe"] = (attributes["minecraft:gameplay/piglins_zombify"] as? Boolean)?.not() ?: false
                    json["has_raids"] = attributes["minecraft:gameplay/can_start_raid"] as? Boolean ?: true
                    json["natural"] = attributes["minecraft:gameplay/nether_portal_spawns_piglin"] as? Boolean
                        ?: (json["has_skylight"] as? Boolean ?: true)
                    val fixed = json.remove("has_fixed_time") as? Boolean ?: false
                    val fixedTime = json.remove("golem:fixed_time")
                    if (fixed) json["fixed_time"] = fixedTime ?: 6000
                    json.remove("timelines")
                    json.remove("default_clock")
                    json.remove("has_ender_dragon_fight")
                    json.remove("cardinal_lighting")
                    file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(json)))
                    converted++
                }
            logger.lifecycle("legacyDimensionCompatibility: converted $converted dimension type(s)")
        }
    }
}

applyLegacyDimensionCompatibility(
    (findProperty("mc_version") ?: error("legacy-dimensions needs extra[\"mc_version\"]")).toString())
