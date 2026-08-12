package dev.golem.core.net;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * Client-to-server send path.
 *
 * <p>Deliberately loader-agnostic: wrapping the payload in a vanilla
 * {@code ServerboundCustomPayloadPacket} and pushing it down the connection works identically on
 * Fabric and NeoForge, and on every version that has the payload API. Both loaders offer their own
 * helper for this, but neither adds anything, so there is no reason to carry two implementations.
 *
 * <p>Kept in its own class because it touches {@code Minecraft}: a dedicated server would fail to load
 * any class that references it, even on a code path never taken.
 */
public final class ClientPacketSender {

    private ClientPacketSender() {}

    /** Sends a payload to the server, or does nothing when not connected. */
    public static void sendToServer(CustomPacketPayload payload) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(payload));
        }
    }
}
