package dev.golem.core.render.gem;

//? if >=1.21.11 {
import dev.golem.core.GolemCore;
import dev.golem.core.client.ClientTicks;
import dev.golem.core.platform.Platform;
import dev.golem.core.render.Frustums;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sodium draws block entities from lists built with each section, and a machine can be left out of them until that
 * section is rebuilt; vanilla draws the global set every frame. A {@link GemBER} machine in view that nobody has asked
 * to render for a while gets its section rebuilt, at most once per {@link #RETRY_NANOS}.
 */
final class StaleSections {

    private static final long STALE_NANOS = 2_000_000_000L;
    private static final long RETRY_NANOS = 10_000_000_000L;
    private static final long FORGET_NANOS = 60_000_000_000L;
    private static final int CHECK_TICKS = 20;

    // Render thread only: shouldRender and the client tick both run there.
    private static final Long2LongOpenHashMap ASKED = new Long2LongOpenHashMap();
    private static final Long2LongOpenHashMap RETRIED = new Long2LongOpenHashMap();
    private static boolean installed;
    private static int ticks;

    private StaleSections() {}

    static void install() {
        if (installed) return;
        installed = true;
        if (Platform.isModLoaded("sodium")) ClientTicks.onEndTick(StaleSections::check);
    }

    static void asked(BlockPos pos) {
        ASKED.put(pos.asLong(), System.nanoTime());
    }

    private static void check() {
        if (++ticks % CHECK_TICKS != 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.levelRenderer == null) return;
        long now = System.nanoTime();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        double reach = minecraft.options.getEffectiveRenderDistance() * 16D;
        for (BlockEntity blockEntity : minecraft.level.getGloballyRenderedBlockEntities()) {
            if (blockEntity.isRemoved()
                    || !(minecraft.getBlockEntityRenderDispatcher().getRenderer(blockEntity) instanceof GemBER<?, ?>)) {
                continue;
            }
            BlockPos pos = blockEntity.getBlockPos();
            long key = pos.asLong();
            // Staleness counts only while the machine stays in view, from the first check that sees it.
            if (!ASKED.containsKey(key) || !Vec3.atCenterOf(pos).closerThan(camera, reach)
                    || !Frustums.isVisible(new AABB(pos).inflate(2D))) {
                ASKED.put(key, now);
                continue;
            }
            if (now - ASKED.get(key) < STALE_NANOS || now - RETRIED.getOrDefault(key, 0L) < RETRY_NANOS) continue;
            minecraft.levelRenderer.setSectionDirty(SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getY()), SectionPos.blockToSectionCoord(pos.getZ()));
            RETRIED.put(key, now);
            GolemCore.LOG.debug("Rebuilding the section of {} at {}: not offered to its renderer for {} ms",
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()), pos, (now - ASKED.get(key)) / 1_000_000L);
        }
        ASKED.long2LongEntrySet().removeIf(entry -> now - entry.getLongValue() > FORGET_NANOS);
        RETRIED.long2LongEntrySet().removeIf(entry -> now - entry.getLongValue() > FORGET_NANOS);
    }
}
//?}
