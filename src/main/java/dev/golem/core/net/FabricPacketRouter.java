package dev.golem.core.net;

//? if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/^*
 * {@link PacketRouter} for Fabric.
 *
 * <p>{@code sendToServer} delegates to {@link ClientPacketSender}, which is loader-agnostic and kept
 * in a separate class so this one stays loadable on a dedicated server.
 ^/
public final class FabricPacketRouter implements PacketRouter {

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToTracking(ServerLevel level, BlockPos pos, CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.tracking(level, pos)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketSender.sendToServer(payload);
    }
}
*///?}
