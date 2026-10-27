package dev.golem.core.net;

import java.util.HashSet;
import java.util.Set;
//? if fabric {
/*import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
*///?}

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

    //? if fabric {
    /*private static final Set<String> INSTALLED = new HashSet<>();
    private static boolean playOwnerHooked;
    *///?}

    //? if fabric && >=1.20.5 {
    /*static synchronized void install() {
        // Called more than once (each mod's client init, then Golem Core at client start); each channel once.
        if (!playOwnerHooked) {
            playOwnerHooked = true;
            ClientPlayConnectionEvents.JOIN.register(
                    (listener, sender, client) -> ClientSessions.bindPlayOwner(sender, listener));
        }
        for (GolemNetwork.ClientRegistration<?> registration : new java.util.ArrayList<>(GolemNetwork.clientRegistrations())) {
            if (INSTALLED.add(registration.codec().id().toString())) register(registration);
        }
    }

    private static <P extends GolemPayload> void register(GolemNetwork.ClientRegistration<P> registration) {
        var type = registration.codec().type();
        boolean offThread = GolemNetwork.isOffThread(registration.codec());
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            if (offThread) {
                GolemNetwork.dispatchClientOffThread(registration.codec(), payload);
                return;
            }
            // This Fabric API already calls the receiver on the render thread, in order with vanilla's packets.
            // Handing it to client().execute() again is not a no-op: inside a packet task the client queues the
            // runnable behind every packet already received, so a payload sent just before a vanilla packet ran after
            // it (the crucible's cut arrived after the victim's removal, and found no entity to slice).
            var session = ClientSessions.forPlayOwner(context.responseSender());
            if (session == null) return;
            Runnable dispatch = () -> {
                if (!session.active() || context.client().level == null || context.client().player == null) return;
                GolemNetwork.dispatchClient(registration.codec(), payload,
                        new GolemNetwork.ClientContext(context.client().level, context.client().player, session));
            };
            if (context.client().isSameThread()) dispatch.run();
            else context.client().execute(dispatch);
        });
    }

    static <P extends GolemPayload> void sendToServer(GolemCodec<P> codec, P payload) {
        ClientPlayNetworking.send(payload);
    }
    *///?} elif fabric {
    /*static synchronized void install() {
        // Called more than once (each mod's client init, then Golem Core at client start); each channel once.
        if (!playOwnerHooked) {
            playOwnerHooked = true;
            ClientPlayConnectionEvents.JOIN.register(
                    (listener, sender, client) -> ClientSessions.bindPlayOwner(sender, listener));
        }
        for (GolemNetwork.ClientRegistration<?> registration : new java.util.ArrayList<>(GolemNetwork.clientRegistrations())) {
            if (INSTALLED.add(registration.codec().id().toString())) register(registration);
        }
    }

    private static <P extends GolemPayload> void register(GolemNetwork.ClientRegistration<P> registration) {
        GolemCodec<P> codec = registration.codec();
        boolean offThread = GolemNetwork.isOffThread(codec);
        ClientPlayNetworking.registerGlobalReceiver(codec.id(), (client, ignored, buffer, sender) -> {
            P payload = codec.decode(buffer);
            if (offThread) {
                GolemNetwork.dispatchClientOffThread(codec, payload);
                return;
            }
            var session = ClientSessions.forListener(ignored);
            if (session == null) return;
            client.execute(() -> {
                if (!session.active() || client.level == null || client.player == null) return;
                GolemNetwork.dispatchClient(registration.codec(), payload,
                        new GolemNetwork.ClientContext(client.level, client.player, session));
            });
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
