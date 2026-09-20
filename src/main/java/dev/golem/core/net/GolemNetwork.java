package dev.golem.core.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
//? if fabric && >=1.20.5 {
/*import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
*///?} elif fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
*///?} elif forge {
/*import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.resources.ResourceLocation;
*///?} elif neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
//?}

/** Loader-neutral play-payload declarations and routing. */
public final class GolemNetwork {

    /** Client state supplied after the payload has been dispatched to the client main thread. */
    public record ClientContext(Level level, Player player) {}

    record ClientRegistration<P extends GolemPayload>(GolemCodec<P> codec,
            BiConsumer<P, ClientContext> handler) {}

    private record ServerRegistration<P extends GolemPayload>(GolemCodec<P> codec,
            BiConsumer<P, ServerPlayer> handler) {}

    private static final List<GolemCodec<?>> CLIENT_DECLARATIONS = new ArrayList<>();
    private static final List<ClientRegistration<?>> CLIENT_REGISTRATIONS = new ArrayList<>();
    private static final List<ServerRegistration<?>> SERVER_REGISTRATIONS = new ArrayList<>();
    private static Supplier<ClientContext> clientContextSupplier;

    /**
     * Channels whose handler runs on the network thread rather than being handed to the game thread.
     *
     * <p>Every loader decodes on the network thread already and then defers the handler, which is the
     * right default: a handler that touches the level, an entity, an item stack or a renderer must run
     * where those are safe to touch. Skipping the hand-off is worth it only for a handler that touches
     * none of them — recording a timestamp, answering a probe, pushing into a concurrent queue — and
     * for those it removes a tick of latency and a scheduled task per packet.
     *
     * <p>The off-thread registration methods hand the handler the payload and nothing else, so the
     * things it must not touch are not reachable from it rather than merely discouraged.
     */
    private static final java.util.Set<String> OFF_THREAD = new java.util.HashSet<>();

    static boolean isOffThread(GolemCodec<?> codec) {
        return OFF_THREAD.contains(codec.id().toString());
    }

    /** Whether this channel's handler runs on the network thread. */
    public static boolean offThread(GolemCodec<?> codec) {
        return isOffThread(codec);
    }

    private GolemNetwork() {}

    /** Declares a clientbound payload in common code without loading client networking classes. */
    public static <P extends GolemPayload> void declarePlayToClient(GolemCodec<P> codec) {
        CLIENT_DECLARATIONS.add(codec);
    }

    /**
     * Declares a clientbound payload whose handler runs on the network thread. Pair with
     * {@link #registerPlayToClientOffThread}; the two must agree, so the channel is marked here where
     * common code declares it rather than on the client where the handler is attached.
     */
    public static <P extends GolemPayload> void declarePlayToClientOffThread(GolemCodec<P> codec) {
        OFF_THREAD.add(codec.id().toString());
        CLIENT_DECLARATIONS.add(codec);
    }

    /**
     * Attaches a clientbound handler. Call only from a physical-client entrypoint.
     *
     * <p>Refuses a channel declared off-thread. That pairing is the one mistake this API can make
     * quietly: an off-thread dispatch has no context to give, so a handler expecting one would be
     * called with null, be dropped by the null guard below, and simply never run.
     */
    public static <P extends GolemPayload> void registerPlayToClient(GolemCodec<P> codec,
            BiConsumer<P, ClientContext> handler) {
        if (isOffThread(codec)) {
            throw new IllegalArgumentException(codec.id()
                    + " is declared off-thread; use registerPlayToClientOffThread");
        }
        CLIENT_REGISTRATIONS.add(new ClientRegistration<>(codec, handler));
    }

    /**
     * Attaches a clientbound handler that runs on the network thread. It is given the payload alone:
     * there is no level and no player, because neither may be touched from here.
     */
    public static <P extends GolemPayload> void registerPlayToClientOffThread(GolemCodec<P> codec,
            java.util.function.Consumer<P> handler) {
        if (!isOffThread(codec)) {
            throw new IllegalArgumentException(codec.id()
                    + " is not declared off-thread; declare it with declarePlayToClientOffThread");
        }
        CLIENT_REGISTRATIONS.add(new ClientRegistration<>(codec, (payload, ignored) -> handler.accept(payload)));
    }

    /** Supplies client state to transports which do not expose it in their receive context. */
    public static void setClientContextSupplier(Supplier<ClientContext> supplier) {
        clientContextSupplier = supplier;
    }

    /** Runs a network-thread handler, which is given no context because it may touch none. */
    public static <P extends GolemPayload> void dispatchClientOffThread(GolemCodec<P> codec, P payload) {
        for (ClientRegistration<?> registration : CLIENT_REGISTRATIONS) {
            dispatchClient(registration, codec, payload, null);
        }
    }

    static <P extends GolemPayload> void dispatchClient(GolemCodec<P> codec, P payload,
            ClientContext context) {
        if (context == null) return;
        for (ClientRegistration<?> registration : CLIENT_REGISTRATIONS) {
            dispatchClient(registration, codec, payload, context);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <P extends GolemPayload> void dispatchClient(ClientRegistration<?> registration,
            GolemCodec<P> codec, P payload, ClientContext context) {
        if (registration.codec() == codec) ((ClientRegistration) registration).handler().accept(payload, context);
    }

    public static <P extends GolemPayload> void registerPlayToServer(GolemCodec<P> codec,
            BiConsumer<P, ServerPlayer> handler) {
        if (isOffThread(codec)) {
            throw new IllegalArgumentException(codec.id()
                    + " is declared off-thread; use registerPlayToServerOffThread");
        }
        SERVER_REGISTRATIONS.add(new ServerRegistration<>(codec, handler));
    }

    /**
     * Attaches a serverbound handler that runs on the network thread. It is given the sender's id
     * rather than the sender: reading the identity is safe from here, and the player object is not.
     */
    public static <P extends GolemPayload> void registerPlayToServerOffThread(GolemCodec<P> codec,
            BiConsumer<P, java.util.UUID> handler) {
        OFF_THREAD.add(codec.id().toString());
        SERVER_REGISTRATIONS.add(new ServerRegistration<>(codec,
                (payload, sender) -> handler.accept(payload, sender.getUUID())));
    }

    // One branch for every payload-registry-era Fabric node. The 26.1 and 1.21.1 fabric-api spell the
    // two registry accessors differently (clientboundPlay/serverboundPlay vs playS2C/playC2S); that is
    // a pure rename, so it is a Stonecutter replacement rather than a second copy of this block.
    //? if fabric && >=1.20.5 {
    /*static List<ClientRegistration<?>> clientRegistrations() {
        return CLIENT_REGISTRATIONS;
    }

    public static void flushCommonRegistrations() {
        CLIENT_DECLARATIONS.forEach(GolemNetwork::registerFabricClientCodec);
        CLIENT_DECLARATIONS.clear();
        flushServerRegistrations();
    }

    public static void flushClientRegistrations() {
        GolemClientNetwork.install();
    }

    public static void flushServerRegistrations() {
        SERVER_REGISTRATIONS.forEach(GolemNetwork::registerFabricServer);
        SERVER_REGISTRATIONS.clear();
    }

    private static <P extends GolemPayload> void registerFabricClientCodec(GolemCodec<P> codec) {
        PayloadTypeRegistry.clientboundPlay().register(codec.type(), codec.streamCodec());
    }

    private static <P extends GolemPayload> void registerFabricServer(ServerRegistration<P> registration) {
        var type = registration.codec().type();
        PayloadTypeRegistry.serverboundPlay().register(type, registration.codec().streamCodec());
        boolean offThread = isOffThread(registration.codec());
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            if (offThread) registration.handler().accept(payload, context.player());
            else context.server().execute(() -> registration.handler().accept(payload, context.player()));
        });
    }

    public static <P extends GolemPayload> void sendToPlayer(ServerPlayer player, GolemCodec<P> codec, P payload) {
        ServerPlayNetworking.send(player, payload);
    }

    /^*
     * Whether {@code player}'s connection has negotiated the channel for {@code codec}, so a send will
     * reach it rather than fail. A player without the mod (or a test's bare in-level mock player, whose
     * connection never ran a handshake) answers false; a broadcast that sweeps {@code level.players()}
     * must skip those, because the loader refuses the send with an exception instead of dropping it.
     ^/
    public static boolean canSendToPlayer(ServerPlayer player, GolemCodec<?> codec) {
        return ServerPlayNetworking.canSend(player, codec.type());
    }

    public static <P extends GolemPayload> void sendToTracking(ServerLevel level, BlockPos pos, GolemCodec<P> codec, P payload) {
        for (ServerPlayer player : PlayerLookup.tracking(level, pos)) ServerPlayNetworking.send(player, payload);
    }

    public static <P extends GolemPayload> void sendToServer(GolemCodec<P> codec, P payload) {
        GolemClientNetwork.sendToServer(codec, payload);
    }
    *///?} elif fabric {
    /*static List<ClientRegistration<?>> clientRegistrations() {
        return CLIENT_REGISTRATIONS;
    }

    public static void flushCommonRegistrations() {
        CLIENT_DECLARATIONS.clear();
        flushServerRegistrations();
    }

    public static void flushClientRegistrations() {
        GolemClientNetwork.install();
    }

    public static void flushServerRegistrations() {
        SERVER_REGISTRATIONS.forEach(GolemNetwork::registerFabricServer);
        SERVER_REGISTRATIONS.clear();
    }

    private static <P extends GolemPayload> void registerFabricServer(ServerRegistration<P> registration) {
        GolemCodec<P> codec = registration.codec();
        boolean offThread = isOffThread(codec);
        ServerPlayNetworking.registerGlobalReceiver(codec.id(), (server, player, ignored, buffer, sender) -> {
            P payload = codec.decode(buffer);
            if (offThread) registration.handler().accept(payload, player);
            else server.execute(() -> registration.handler().accept(payload, player));
        });
    }

    public static <P extends GolemPayload> void sendToPlayer(ServerPlayer player, GolemCodec<P> codec, P payload) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        codec.encode(payload, buffer);
        ServerPlayNetworking.send(player, codec.id(), buffer);
    }

    /^*
     * Whether {@code player}'s connection has negotiated the channel for {@code codec}, so a send will
     * reach it rather than fail. A player without the mod (or a test's bare in-level mock player, whose
     * connection never ran a handshake) answers false; a broadcast that sweeps {@code level.players()}
     * must skip those, because the loader refuses the send with an exception instead of dropping it.
     ^/
    public static boolean canSendToPlayer(ServerPlayer player, GolemCodec<?> codec) {
        return ServerPlayNetworking.canSend(player, codec.id());
    }

    public static <P extends GolemPayload> void sendToTracking(ServerLevel level, BlockPos pos, GolemCodec<P> codec, P payload) {
        for (ServerPlayer player : PlayerLookup.tracking(level, pos)) sendToPlayer(player, codec, payload);
    }

    public static <P extends GolemPayload> void sendToServer(GolemCodec<P> codec, P payload) {
        GolemClientNetwork.sendToServer(codec, payload);
    }
    *///?} elif forge {
    /*private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation("golemcore", "play"))
            .networkProtocolVersion(() -> "1")
            .clientAcceptedVersions("1"::equals)
            .serverAcceptedVersions("1"::equals)
            .simpleChannel();
    private static int nextMessageId;

    // SimpleChannel writes this index as one byte ahead of the body, so the accelerated transport has
    // to reproduce it to build the same wire. Nothing else needs it.
    private static final java.util.Map<String, Integer> FORGE_DISCRIMINATORS = new java.util.HashMap<>();

    static int forgeDiscriminator(GolemCodec<?> codec) {
        Integer index = FORGE_DISCRIMINATORS.get(codec.id().toString());
        if (index == null) throw new IllegalStateException("No Forge message index for " + codec.id());
        return index;
    }

    public static void flushCommonRegistrations() {
        CLIENT_DECLARATIONS.forEach(GolemNetwork::registerForgeClient);
        CLIENT_DECLARATIONS.clear();
        flushServerRegistrations();
    }

    public static void flushClientRegistrations() {}

    public static void flushServerRegistrations() {
        SERVER_REGISTRATIONS.forEach(GolemNetwork::registerForgeServer);
        SERVER_REGISTRATIONS.clear();
    }

    private static <P extends GolemPayload> void registerForgeClient(GolemCodec<P> codec) {
        FORGE_DISCRIMINATORS.put(codec.id().toString(), nextMessageId);
        var builder = CHANNEL.messageBuilder(codec.payloadType(), nextMessageId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(codec::encode).decoder(codec::decode);
        if (isOffThread(codec)) {
            builder.consumerNetworkThread((payload, context) -> {
                dispatchClientOffThread(codec, payload);
                context.get().setPacketHandled(true);
            }).add();
            return;
        }
        builder.consumerMainThread((payload, context) -> dispatchClient(codec, payload,
                clientContextSupplier == null ? null : clientContextSupplier.get())).add();
    }

    private static <P extends GolemPayload> void registerForgeServer(ServerRegistration<P> registration) {
        GolemCodec<P> codec = registration.codec();
        var builder = CHANNEL.messageBuilder(codec.payloadType(), nextMessageId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(codec::encode).decoder(codec::decode);
        if (isOffThread(codec)) {
            builder.consumerNetworkThread((payload, context) -> {
                registration.handler().accept(payload, context.get().getSender());
                context.get().setPacketHandled(true);
            }).add();
            return;
        }
        builder.consumerMainThread((payload, context) ->
                registration.handler().accept(payload, context.get().getSender())).add();
    }

    public static <P extends GolemPayload> void sendToPlayer(ServerPlayer player, GolemCodec<P> codec, P payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    /^*
     * Whether {@code player}'s connection has negotiated the channel for {@code codec}, so a send will
     * reach it rather than fail. A player without the mod (or a test's bare in-level mock player, whose
     * connection never ran a handshake) answers false; a broadcast that sweeps {@code level.players()}
     * must skip those, because the loader refuses the send with an exception instead of dropping it.
     ^/
    public static boolean canSendToPlayer(ServerPlayer player, GolemCodec<?> codec) {
        return CHANNEL.isRemotePresent(player.connection.connection);
    }

    public static <P extends GolemPayload> void sendToTracking(ServerLevel level, BlockPos pos, GolemCodec<P> codec, P payload) {
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)), payload);
    }

    public static <P extends GolemPayload> void sendToServer(GolemCodec<P> codec, P payload) {
        CHANNEL.sendToServer(payload);
    }
    *///?} elif neoforge {
    public static void flushRegistrations(RegisterPayloadHandlersEvent event) {
        CLIENT_DECLARATIONS.forEach(registration -> registerNeoForgeClient(event, registration));
        SERVER_REGISTRATIONS.forEach(registration -> registerNeoForgeServer(event, registration));
        CLIENT_DECLARATIONS.clear();
        SERVER_REGISTRATIONS.clear();
    }

    private static <P extends GolemPayload> void registerNeoForgeClient(RegisterPayloadHandlersEvent event,
            GolemCodec<P> codec) {
        boolean offThread = isOffThread(codec);
        event.registrar(codec.id().getNamespace()).playToClient(codec.type(),
                codec.<net.minecraft.network.RegistryFriendlyByteBuf>streamCodec(),
                (payload, context) -> {
                    if (offThread) {
                        dispatchClientOffThread(codec, payload);
                        return;
                    }
                    context.enqueueWork(() -> dispatchClient(codec, payload,
                            new ClientContext(context.player().level(), context.player())));
                });
    }

    private static <P extends GolemPayload> void registerNeoForgeServer(RegisterPayloadHandlersEvent event,
            ServerRegistration<P> registration) {
        boolean offThread = isOffThread(registration.codec());
        event.registrar(registration.codec().id().getNamespace()).playToServer(registration.codec().type(),
                registration.codec().<net.minecraft.network.RegistryFriendlyByteBuf>streamCodec(),
                (payload, context) -> {
                    if (offThread) {
                        registration.handler().accept(payload, (ServerPlayer) context.player());
                        return;
                    }
                    context.enqueueWork(() -> registration.handler().accept(payload,
                            (ServerPlayer) context.player()));
                });
    }

    public static <P extends GolemPayload> void sendToPlayer(ServerPlayer player, GolemCodec<P> codec, P payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    /**
     * Whether {@code player}'s connection has negotiated the channel for {@code codec}, so a send will
     * reach it rather than fail. A player without the mod (or a test's bare in-level mock player, whose
     * connection never ran a handshake) answers false; a broadcast that sweeps {@code level.players()}
     * must skip those, because the loader refuses the send with an exception instead of dropping it.
     */
    public static boolean canSendToPlayer(ServerPlayer player, GolemCodec<?> codec) {
        return player.connection.hasChannel(codec.type());
    }

    public static <P extends GolemPayload> void sendToTracking(ServerLevel level, BlockPos pos, GolemCodec<P> codec, P payload) {
        PacketDistributor.sendToPlayersTrackingChunk(level,
                new net.minecraft.world.level.ChunkPos(pos.getX() >> 4, pos.getZ() >> 4), payload);
    }

    // Delegated rather than inlined: this class is loaded on dedicated servers, and Minecraft.getInstance()
    // is a client-only type. Keeping the reference inside GolemClientNetwork means the server JVM never
    // has a reason to resolve it, instead of relying on lazy method verification to save us.
    public static <P extends GolemPayload> void sendToServer(GolemCodec<P> codec, P payload) {
        GolemClientNetwork.sendToServer(codec, payload);
    }
    //?}
}
