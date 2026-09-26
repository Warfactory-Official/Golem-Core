plugins {
    id("dev.golem.stonecutter")
}

stonecutter active "26.1-neoforge" /* [SC] DO NOT EDIT */

golem.optional("jei", "kubejs")

stonecutter parameters {
    // Core's own renames (consumers get build-logic's renames.tsv): each reversible, neither spelling in the other era.
    replacements {
        string(current.parsed < "26.1") {
            replace("PayloadTypeRegistry.clientboundPlay()", "PayloadTypeRegistry.playS2C()")
            replace("PayloadTypeRegistry.serverboundPlay()", "PayloadTypeRegistry.playC2S()")
            replace("BuiltInRegistries.ITEM.getValue(Ids.parse(", "BuiltInRegistries.ITEM.get(Ids.parse(")
            replace("BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Ids.parse(",
                    "BuiltInRegistries.DATA_COMPONENT_TYPE.get(Ids.parse(")
            replace("net.minecraft.world.entity.animal.chicken.Chicken", "net.minecraft.world.entity.animal.Chicken")
            replace("net.minecraft.world.entity.animal.squid.Squid", "net.minecraft.world.entity.animal.Squid")
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
