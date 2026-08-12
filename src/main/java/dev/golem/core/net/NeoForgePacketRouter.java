package dev.golem.core.net;

//? if neoforge {
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * {@link PacketRouter} for NeoForge. {@code PacketDistributor} is unchanged between 1.21.1 and 26.1, so
 * this needs no version guards of its own.
 */
public final class NeoForgePacketRouter implements PacketRouter {

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToTracking(ServerLevel level, BlockPos pos, CustomPacketPayload payload) {
        // Tracking the *chunk*, not the block: this is the block-entity sync path, and a player who has
        // the chunk loaded needs the update even when far from the block itself.
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4), payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketSender.sendToServer(payload);
    }
}
//?}
