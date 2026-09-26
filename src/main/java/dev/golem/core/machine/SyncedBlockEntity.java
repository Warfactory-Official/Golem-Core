package dev.golem.core.machine;

import dev.golem.core.data.CompatBlockEntity;
import dev.golem.core.graph.GraphResident;
import dev.golem.core.net.GolemBuffers;
import dev.golem.core.net.GolemSync;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
//?} else {
/*import net.minecraft.network.FriendlyByteBuf;
*///?}
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity with two client paths: whole saved tag on chunk load, and a {@link StateSync} blob pushed from the
 * server tick ({@link #syncState}/{@link #syncStateIfChanged}) through {@link GolemSync} — built only while watched,
 * never re-sent unchanged, replayed to late arrivals. Server thread; no off-thread packet pool. Arrival notifies
 * the node graph ({@link GraphResident}).
 */
public class SyncedBlockEntity extends CompatBlockEntity implements StateSync, GraphResident {

    /** Gates {@link #syncStateIfChanged}. */
    protected boolean hasDataChanged = true;
    private long lastSentHash;

    public SyncedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GraphResident.onLoad(this);
    }

    public void dataChanged() {
        hasDataChanged = true;
    }

    @Override
    protected void loadData(dev.golem.core.data.DataIn input) {
        super.loadData(input);
        hasDataChanged = true;
    }

    @Override
    protected boolean syncFullTagOnChunkLoad() {
        return true;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Publishes {@link #serialize} to trackers; unwatched => not built; byte-identical to last => not sent. */
    public void syncState() {
        if (!(level instanceof ServerLevel serverLevel) || !GolemSync.isWatched(serverLevel, worldPosition)) return;
        byte[] data = compileState();
        long hash = fnv1a64(data);
        if (hash == lastSentHash) return;
        lastSentHash = hash;
        GolemSync.publish(serverLevel, worldPosition, StateSyncPayload.CODEC, new StateSyncPayload(worldPosition, data));
    }

    /**
     * {@link #syncState} only after {@link #dataChanged}. A change while unwatched keeps its flag => first watched tick
     * sends it.
     */
    public void syncStateIfChanged() {
        if (!hasDataChanged || !(level instanceof ServerLevel serverLevel)
                || !GolemSync.isWatched(serverLevel, worldPosition)) return;
        byte[] data = compileState();
        lastSentHash = fnv1a64(data);
        GolemSync.publish(serverLevel, worldPosition, StateSyncPayload.CODEC, new StateSyncPayload(worldPosition, data));
        hasDataChanged = false;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) GolemSync.retract(serverLevel, worldPosition);
        super.setRemoved();
    }

    private byte[] compileState() {
        var buf = GolemBuffers.wrap(Unpooled.buffer(), level);
        try {
            serialize(buf);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return data;
        } finally {
            buf.release();
        }
    }

    private static long fnv1a64(byte[] data) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : data) {
            hash ^= b & 0xFFL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    @Override
    public void serialize(
            //? if >=1.20.5 {
            RegistryFriendlyByteBuf
            //?} else {
            /*FriendlyByteBuf
            *///?}
            buf) {}

    @Override
    public void deserialize(
            //? if >=1.20.5 {
            RegistryFriendlyByteBuf
            //?} else {
            /*FriendlyByteBuf
            *///?}
            buf) {}
}
