package dev.golem.core.world;

import dev.golem.core.Ids;

//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import net.minecraft.sounds.SoundEvent;

/** Sound registry operations whose lookup spelling changed with the registry API. */
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

    public static SoundEvent minecraft(String path) {
        return get(Ids.minecraft(path));
    }
}
