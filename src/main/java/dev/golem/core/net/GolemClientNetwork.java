package dev.golem.core.net;

//? if fabric && >=1.20.5 {
/*import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
*///?} elif fabric {
/*import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
*///?}

/**
 * Client-side networking bridge. Every reference to a client-only type lives here rather than in
 * {@link GolemNetwork}, which is loaded on dedicated servers too. This class is never loaded by
 * common initialization.
 */
final class GolemClientNetwork {

    private GolemClientNetwork() {}

    //? if fabric && >=1.20.5 {
    /*static void install() {
        GolemNetwork.clientRegistrations().forEach(GolemClientNetwork::register);
    }

    private static <P extends GolemPayload> void register(GolemNetwork.ClientRegistration<P> registration) {
        var type = registration.codec().type();
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> context.client().execute(() ->
                GolemNetwork.dispatchClient(registration.codec(), payload,
                        new GolemNetwork.ClientContext(context.client().level, context.client().player))));
    }

    static <P extends GolemPayload> void sendToServer(GolemCodec<P> codec, P payload) {
        ClientPlayNetworking.send(payload);
    }
    *///?} elif fabric {
    /*static void install() {
        GolemNetwork.clientRegistrations().forEach(GolemClientNetwork::register);
    }

    private static <P extends GolemPayload> void register(GolemNetwork.ClientRegistration<P> registration) {
        GolemCodec<P> codec = registration.codec();
        ClientPlayNetworking.registerGlobalReceiver(codec.id(), (client, ignored, buffer, sender) -> {
            P payload = codec.decode(buffer);
            client.execute(() -> GolemNetwork.dispatchClient(codec, payload,
                    new GolemNetwork.ClientContext(client.level, client.player)));
        });
    }

    static <P extends GolemPayload> void sendToServer(GolemCodec<P> codec, P payload) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        codec.encode(payload, buffer);
        ClientPlayNetworking.send(codec.id(), buffer);
    }
    *///?}

    // NeoForge receives through the RegisterPayloadHandlersEvent registrar, so only the send half
    // needs a home here. Forge is absent on purpose: its SimpleChannel.sendToServer touches no
    // client-only type, so routing it through this class would buy nothing.
    //? if neoforge {
    static <P extends GolemPayload> void sendToServer(GolemCodec<P> codec, P payload) {
        net.minecraft.client.multiplayer.ClientPacketListener connection =
                net.minecraft.client.Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(payload));
        }
    }
    //?}
}
