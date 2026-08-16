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

    private GolemNetwork() {}

    /** Declares a clientbound payload in common code without loading client networking classes. */
    public static <P extends GolemPayload> void declarePlayToClient(GolemCodec<P> codec) {
        CLIENT_DECLARATIONS.add(codec);
    }

    /** Attaches a clientbound handler. Call only from a physical-client entrypoint. */
    public static <P extends GolemPayload> void registerPlayToClient(GolemCodec<P> codec,
            BiConsumer<P, ClientContext> handler) {
        CLIENT_REGISTRATIONS.add(new ClientRegistration<>(codec, handler));
    }

    /** Supplies client state to transports which do not expose it in their receive context. */
    public static void setClientContextSupplier(Supplier<ClientContext> supplier) {
        clientContextSupplier = supplier;
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
        SERVER_REGISTRATIONS.add(new ServerRegistration<>(codec, handler));
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
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> context.server().execute(() ->
                registration.handler().accept(payload, context.player())));
    }

    public static <P extends GolemPayload> void sendToPlayer(ServerPlayer player, GolemCodec<P> codec, P payload) {
        ServerPlayNetworking.send(player, payload);
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
        ServerPlayNetworking.registerGlobalReceiver(codec.id(), (server, player, ignored, buffer, sender) -> {
            P payload = codec.decode(buffer);
            server.execute(() -> registration.handler().accept(payload, player));
        });
    }

    public static <P extends GolemPayload> void sendToPlayer(ServerPlayer player, GolemCodec<P> codec, P payload) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        codec.encode(payload, buffer);
        ServerPlayNetworking.send(player, codec.id(), buffer);
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
        CHANNEL.messageBuilder(codec.payloadType(), nextMessageId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(codec::encode).decoder(codec::decode).consumerMainThread((payload, context) ->
                        dispatchClient(codec, payload,
                                clientContextSupplier == null ? null : clientContextSupplier.get())).add();
    }

    private static <P extends GolemPayload> void registerForgeServer(ServerRegistration<P> registration) {
        GolemCodec<P> codec = registration.codec();
        CHANNEL.messageBuilder(codec.payloadType(), nextMessageId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(codec::encode).decoder(codec::decode)
                .consumerMainThread((payload, context) -> registration.handler().accept(payload, context.get().getSender())).add();
    }

    public static <P extends GolemPayload> void sendToPlayer(ServerPlayer player, GolemCodec<P> codec, P payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
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
        event.registrar(codec.id().getNamespace()).playToClient(codec.type(),
                codec.<net.minecraft.network.RegistryFriendlyByteBuf>streamCodec(),
                (payload, context) -> context.enqueueWork(() -> dispatchClient(codec, payload,
                        new ClientContext(context.player().level(), context.player()))));
    }

    private static <P extends GolemPayload> void registerNeoForgeServer(RegisterPayloadHandlersEvent event,
            ServerRegistration<P> registration) {
        event.registrar(registration.codec().id().getNamespace()).playToServer(registration.codec().type(),
                registration.codec().<net.minecraft.network.RegistryFriendlyByteBuf>streamCodec(),
                (payload, context) -> context.enqueueWork(() -> registration.handler().accept(payload,
                        (ServerPlayer) context.player())));
    }

    public static <P extends GolemPayload> void sendToPlayer(ServerPlayer player, GolemCodec<P> codec, P payload) {
        PacketDistributor.sendToPlayer(player, payload);
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
