package dev.golem.core.machine;

import dev.golem.core.GolemCore;
import dev.golem.core.Ids;
import dev.golem.core.net.GolemBuffers;
import dev.golem.core.net.GolemCodec;
import dev.golem.core.net.GolemNetwork;
import dev.golem.core.net.GolemPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** {@link StateSync} blob for the block entity at {@code pos}. */
public record StateSyncPayload(BlockPos pos, byte[] data) implements GolemPayload {

    public static final GolemCodec<StateSyncPayload> CODEC = GolemCodec.of(StateSyncPayload.class,
            Ids.of(GolemCore.MOD_ID, "be_sync"),
            (payload, buffer) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeByteArray(payload.data);
            },
            buffer -> new StateSyncPayload(buffer.readBlockPos(), buffer.readByteArray()));

    @Override
    public
    //? if >=26.1 {
    net.minecraft.resources.Identifier
    //?} else {
    /*net.minecraft.resources.ResourceLocation
    *///?}
    id() {
        return CODEC.id();
    }

    /** Core bootstrap. */
    public static void declare() {
        GolemNetwork.declarePlayToClient(CODEC);
    }

    /** Core client bootstrap. */
    public static void registerClient() {
        GolemNetwork.registerPlayToClient(CODEC, (payload, context) -> apply(context.level(), payload.pos, payload.data));
    }

    /** Client main thread. Malformed/underflowing blob => warning, never a torn-down pipeline. */
    public static void apply(Level level, BlockPos pos, byte[] data) {
        if (level == null || !(level.getBlockEntity(pos) instanceof StateSync receiver)) return;
        var buf = GolemBuffers.wrap(Unpooled.wrappedBuffer(data), level);
        try {
            receiver.deserialize(buf);
        } catch (Exception e) {
            GolemCore.LOG.warn("Failed to apply block entity state at {}: {}", pos, e.toString());
        } finally {
            buf.release();
        }
    }
}
