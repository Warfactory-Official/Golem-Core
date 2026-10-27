package dev.golem.core.world;

import dev.golem.core.Ids;

//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** Sound registry lookup and public entity vibrations across the registry and game-event APIs. */
public final class Sounds {

    private Sounds() {}

    public static SoundEvent get(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            id) {
        //? if >=26.1 {
        return net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(id);
        //?} else {
        /*return net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(id);
        *///?}
    }

    /** Public equipment noise: ENTITY_ACTION replaced the frequency-four ENTITY_ROAR event after 1.20.1. */
    public static void entityNoise(Entity source, double x, double y, double z, float volume) {
        if (volume > 0F && source.level() instanceof ServerLevel server) {
            server.gameEvent(
                    //? if >=1.21.1 {
                    GameEvent.ENTITY_ACTION,
                    //?} else {
                    /*GameEvent.ENTITY_ROAR,
                    *///?}
                    new Vec3(x, y, z), GameEvent.Context.of(source));
        }
    }

    public static SoundEvent minecraft(String path) {
        return get(Ids.minecraft(path));
    }
}
