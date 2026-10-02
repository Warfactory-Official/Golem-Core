package dev.golem.core.client;

import net.minecraft.client.player.AbstractClientPlayer;

//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * A player's skin texture - legacy {@code AbstractClientPlayer.getLocationSkin()}. 1.20.1 asks the player directly,
 * 1.21 reads it off the {@code PlayerSkin} record, 26.1 off that record's body asset. Client-only.
 */
public final class PlayerSkins {

    private PlayerSkins() {}

    //? if >=26.1 {
    public static Identifier texture(AbstractClientPlayer player) {
        return player.getSkin().body().texturePath();
    }
    //?} elif >=1.21 {
    /*public static ResourceLocation texture(AbstractClientPlayer player) {
        return player.getSkin().texture();
    }
    *///?} else {
    /*public static ResourceLocation texture(AbstractClientPlayer player) {
        return player.getSkinTextureLocation();
    }
    *///?}
}
