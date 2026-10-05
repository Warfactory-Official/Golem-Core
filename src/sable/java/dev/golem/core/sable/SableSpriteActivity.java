package dev.golem.core.sable;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.caffeinemc.mods.sodium.api.texture.SpriteUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Keeps animated block textures on Sable sub-levels animating under Sodium.
 *
 * <p>Sodium's "animate only visible textures" (on by default) advances an animated sprite only when a renderer marked
 * it drawn that frame; its own chunk renderer does that for the sections it draws, and its item/GUI hooks for what
 * they draw. Sable draws a sub-level from meshes it compiled itself, so nothing marks the sprites on it: a ship's
 * conveyor belts (or any animated block on it) froze unless the same sprite happened to be drawn elsewhere too, such
 * as a held conveyor or a belt in the world. Measured before this class: 0 changed pixels between two frames of a
 * ship's belts, 54,594 with a conveyor held (harness {@code SableShipAnimation}).
 *
 * <p>Each frame this marks, through Sodium's public {@link SpriteUtil}, the animated sprites of every sub-level Sable
 * reports visible sections for. A sub-level's sprites are read from the block states in its plot (section palettes,
 * block models and fluid textures) and re-read about once a second, so blocks added or removed on a ship are picked
 * up. Only loaded with both Sable and Sodium present; without Sodium vanilla ticks every animation anyway. The loader
 * roots call {@link #enable} at client setup and {@link #frame} once per frame ({@link SableIntegration}).
 */
final class SableSpriteActivity {

    private SableSpriteActivity() {}

    /** Game ticks between re-reads of a sub-level's blocks. */
    private static final long REREAD_TICKS = 20L;

    private record Sprites(List<TextureAtlasSprite> animated, long readAt) {}

    private static final Map<UUID, Sprites> SPRITES = new HashMap<>();

    /** The loader's fluid sprites (NeoForge's client fluid extensions, Fabric's fluid render handlers); null = off. */
    private static Function<FluidState, List<TextureAtlasSprite>> fluidSprites;

    static void enable(Function<FluidState, List<TextureAtlasSprite>> fluids) {
        fluidSprites = fluids;
    }

    static void frame() {
        if (fluidSprites == null) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            SPRITES.clear();
            return;
        }
        ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        long now = level.getGameTime();
        Set<UUID> live = new HashSet<>();
        for (ClientSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;
            UUID id = subLevel.getUniqueId();
            live.add(id);
            if (subLevel.getRenderData().getVisibleSectionCount() <= 0) continue;
            Sprites sprites = SPRITES.get(id);
            if (sprites == null || now - sprites.readAt() >= REREAD_TICKS || now < sprites.readAt()) {
                sprites = new Sprites(read(level, subLevel), now);
                SPRITES.put(id, sprites);
            }
            for (TextureAtlasSprite sprite : sprites.animated()) SpriteUtil.INSTANCE.markSpriteActive(sprite);
        }
        SPRITES.keySet().retainAll(live);
    }

    /** The animated sprites of every block state and fluid in the sub-level's plot. */
    private static List<TextureAtlasSprite> read(ClientLevel level, ClientSubLevel subLevel) {
        BoundingBox3ic box = subLevel.getPlot().getBoundingBox();
        Set<BlockState> states = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir()) continue;
                    int minY = chunk.getSectionYFromSectionIndex(i) << 4;
                    if (minY + 15 < box.minY() || minY > box.maxY()) continue;
                    section.getStates().getAll(states::add);
                }
            }
        }

        Set<TextureAtlasSprite> animated = new LinkedHashSet<>();
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create();
        for (BlockState state : states) {
            if (state.getRenderShape() == RenderShape.MODEL) {
                var model = blocks.getBlockModel(state);
                for (Direction side : sidesAndNone()) {
                    random.setSeed(42L);
                    for (BakedQuad quad : model.getQuads(state, side, random)) addIfAnimated(animated, quad.getSprite());
                }
            }
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                for (TextureAtlasSprite sprite : fluidSprites.apply(fluid)) addIfAnimated(animated, sprite);
            }
        }
        return new ArrayList<>(animated);
    }

    private static void addIfAnimated(Set<TextureAtlasSprite> into, TextureAtlasSprite sprite) {
        if (sprite != null && SpriteUtil.INSTANCE.hasAnimation(sprite)) into.add(sprite);
    }

    private static final Direction[] SIDES_AND_NONE;

    static {
        Direction[] sides = Direction.values();
        SIDES_AND_NONE = new Direction[sides.length + 1];
        System.arraycopy(sides, 0, SIDES_AND_NONE, 0, sides.length);
        SIDES_AND_NONE[sides.length] = null;
    }

    private static Direction[] sidesAndNone() {
        return SIDES_AND_NONE;
    }
}
