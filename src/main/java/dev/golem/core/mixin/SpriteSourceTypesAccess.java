package dev.golem.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The sprite source type registry, which vanilla keeps private and only NeoForge publishes an event
 * for. Fabric and Forge register through this instead; see {@code Sprites.install()}.
 *
 * <p>26.1 replaced the {@code BiMap} of types with a late-bound id mapper of codecs, which is why the
 * accessor changes shape rather than just its name.
 */
//? if >=26.1 {
@Mixin(net.minecraft.client.renderer.texture.atlas.SpriteSources.class)
public interface SpriteSourceTypesAccess {

    @Accessor("ID_MAPPER")
    static net.minecraft.util.ExtraCodecs.LateBoundIdMapper<net.minecraft.resources.Identifier,
            com.mojang.serialization.MapCodec<? extends net.minecraft.client.renderer.texture.atlas.SpriteSource>>
            golem$idMapper() {
        throw new AssertionError("mixin accessor not applied");
    }
}
//?} else {
/*@Mixin(net.minecraft.client.renderer.texture.atlas.SpriteSources.class)
public interface SpriteSourceTypesAccess {

    @Accessor("TYPES")
    static com.google.common.collect.BiMap<net.minecraft.resources.ResourceLocation,
            net.minecraft.client.renderer.texture.atlas.SpriteSourceType> golem$types() {
        throw new AssertionError("mixin accessor not applied");
    }
}
*///?}
