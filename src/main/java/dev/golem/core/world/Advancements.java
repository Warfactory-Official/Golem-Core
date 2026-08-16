package dev.golem.core.world;

import net.minecraft.server.level.ServerPlayer;

/** Cross-version advancement lookup and criterion award. */
public final class Advancements {

    private Advancements() {}

    public static void award(ServerPlayer player, String id, String criterion) {
        //? if >=1.21 {
        var advancement = player.level().getServer().getAdvancements().get(dev.golem.core.Ids.parse(id));
        if (advancement != null) player.getAdvancements().award(advancement, criterion);
        //?} else {
        /*var advancement = player.level().getServer().getAdvancements().getAdvancement(dev.golem.core.Ids.parse(id));
        if (advancement != null) player.getAdvancements().award(advancement, criterion);
        *///?}
    }
}
