package dev.golem.core.sable;

import dev.golem.core.GolemCore;
import dev.golem.core.world.SubLevels;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.FluidState;

import java.util.List;
import java.util.function.Function;

/**
 * The loader-neutral half of the Sable integration. The loader roots ({@code src/sable-neoforge},
 * {@code src/sable-fabric}) call these from their own entry points, and only when the {@code sable} mod is loaded.
 */
public final class SableIntegration {

    private SableIntegration() {}

    /**
     * Common setup: Sable answers {@link SubLevels}, and splits keep resolved structures whole.
     *
     * @param carriesInstancedVisuals whether this loader's Sable draws Flywheel visuals with its sub-levels (its
     *                                NeoForge build embeds them; its Fabric build does not)
     */
    public static void setup(boolean carriesInstancedVisuals) {
        SubLevels.install(new SableSubLevels(carriesInstancedVisuals));
        SableSubLevels.keepStructuresWhole();
        GolemCore.LOG.info("Sable present: sub-level positions, velocities and structure splits are Sable's");
    }

    /** End of every server tick: closes a block move whose {@code moveBlocks} never returned. */
    public static void endOfServerTick() {
        SableMoves.closeAbandoned();
    }

    /**
     * Client setup when Sodium is loaded as well: animated sprites on visible sub-levels are marked drawn, so Sodium
     * keeps animating them. The loader calls {@link #renderFrame} once per frame afterwards.
     *
     * @param fluidSprites the still and flowing sprites of a fluid, as the loader's fluid rendering names them
     */
    public static void clientSetupWithSodium(Function<FluidState, List<TextureAtlasSprite>> fluidSprites) {
        SableSpriteActivity.enable(fluidSprites);
        GolemCore.LOG.info("Sable and Sodium present: animated textures on sub-levels are marked visible");
    }

    /** Once per rendered frame, client only. */
    public static void renderFrame() {
        SableSpriteActivity.frame();
    }
}
