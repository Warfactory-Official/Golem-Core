plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1-neoforge" /* [SC] DO NOT EDIT */

// Builds every node in one invocation. Stonecutter 0.9 replaced the old `registerChiseled` API with
// task aggregation: `stonecutter.tasks.named` returns a lazy collection of that task across all nodes.
tasks.register("chiseledBuild") {
    group = "build"
    description = "Builds every registered version/loader node."
    dependsOn(stonecutter.tasks.named("build"))
}

// Uploads every node's jar to Modrinth and CurseForge (Golem-Core/gradle/mod/publish.gradle.kts). Add
// -Ppublish.dryRun=true to build and log the uploads without making them.
tasks.register("publishAllMods") {
    group = "publishing"
    description = "Publishes every version/loader node to Modrinth and CurseForge."
    dependsOn(stonecutter.tasks.named("publishMods"))
}

stonecutter parameters {
    val loader = node.metadata.project.substringAfterLast('-')

    // Loader constants, used as `//? if fabric {` / `//? if forgelike {`.
    constants["fabric"] = loader == "fabric"
    constants["neoforge"] = loader == "neoforge"
    constants["forge"] = loader == "forge"
    constants["forgelike"] = loader == "neoforge" || loader == "forge"

    // Version comparisons use the node's logical version (26.1, 1.21.1, ...), so `//? if >=1.21.11`
    // works regardless of the loader suffix in the node name.
    // True only where the node declares JEI coordinates, so the integration compiles out elsewhere.
    constants["jei"] = !(node.project.findProperty("deps.jei") as String?).isNullOrBlank()
    constants["rei"] = !(node.project.findProperty("deps.rei") as String?).isNullOrBlank()
    // True only where the node declares CC: Tweaked coordinates (an optional peer, like JEI).
    constants["computercraft"] = !(node.project.findProperty("deps.cc") as String?).isNullOrBlank()
    // True only where the node declares KubeJS coordinates (an optional peer; dev.golem.core.kubejs in Golem Core).
    constants["kubejs"] = !(node.project.findProperty("deps.kubejs") as String?).isNullOrBlank()
    // True only where the node declares Jade coordinates (an optional peer; dev.golem.core.jade in Golem Core).
    constants["jade"] = !(node.project.findProperty("deps.jade") as String?).isNullOrBlank()
    // True only where the node declares Applied Energistics 2 coordinates (an optional peer; dev.golem.core.ae2).
    constants["ae2"] = !(node.project.findProperty("deps.ae2") as String?).isNullOrBlank()

    swaps["mc_version"] = "\"${node.metadata.version}\";"

    // Mechanical renames, declared 26.1 -> older because 26.1 is this layer's source of truth. Keep
    // this list minimal: a replacement must be injective in BOTH directions, so only names that
    // cannot appear in the other era belong here. Anything needing real translation gets a facade.
    replacements {
        // Fabric API's own rename, not vanilla's. Qualified with the owning type so the rule can only
        // ever hit these calls, and reversible: neither spelling exists in the other era. Without
        // this, GolemNetwork would need two near-identical fabric branches differing by two tokens.
        string(current.parsed < "26.1") {
            replace("PayloadTypeRegistry.clientboundPlay()", "PayloadTypeRegistry.playS2C()")
            replace("PayloadTypeRegistry.serverboundPlay()", "PayloadTypeRegistry.playC2S()")
            replace("PayloadTypeRegistry.clientboundConfiguration()", "PayloadTypeRegistry.configurationS2C()")
            replace("PayloadTypeRegistry.serverboundConfiguration()", "PayloadTypeRegistry.configurationC2S()")
            replace("configurationContext.packetListener()", "configurationContext.networkHandler()")
            replace("FabricServerConfigurationPacketListenerImpl", "FabricServerConfigurationNetworkHandler")
        }
        string(current.parsed < "26.1") {
            replace("BuiltInRegistries.ITEM.getValue(Ids.parse(", "BuiltInRegistries.ITEM.get(Ids.parse(")
            replace("BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Ids.parse(",
                    "BuiltInRegistries.DATA_COMPONENT_TYPE.get(Ids.parse(")
        }
        // 26.1 moved these vanilla entity classes into per-animal/per-vehicle packages. The old
        // spellings cannot occur in 26.1 code and the new ones cannot occur below it, so the rename
        // is reversible; the entity facades in dev.golem.core.data extend them by 26.1 name.
        string(current.parsed < "26.1") {
            replace("net.minecraft.world.entity.animal.chicken.Chicken", "net.minecraft.world.entity.animal.Chicken")
            replace("net.minecraft.world.entity.animal.squid.Squid", "net.minecraft.world.entity.animal.Squid")
            replace("net.minecraft.world.entity.animal.cow.Cow", "net.minecraft.world.entity.animal.Cow")
            replace("net.minecraft.world.entity.animal.fish.WaterAnimal", "net.minecraft.world.entity.animal.WaterAnimal")
            replace("net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer",
                    "net.minecraft.world.entity.vehicle.AbstractMinecartContainer")
            replace("net.minecraft.world.entity.vehicle.minecart.AbstractMinecart",
                    "net.minecraft.world.entity.vehicle.AbstractMinecart")
            replace("net.minecraft.world.entity.vehicle.minecart.MinecartTNT",
                    "net.minecraft.world.entity.vehicle.MinecartTNT")
            replace("net.minecraft.world.entity.vehicle.boat.Boat", "net.minecraft.world.entity.vehicle.Boat")
        }
    }
}
