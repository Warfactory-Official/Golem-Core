package dev.golem.core.net;

//? if fabric && >=1.21 {
/*import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
*///?}
//? if fabric && >=1.21 {
/*import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl;
*///?}
//? if fabric && >=1.21 {
/*import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
*///?}
//? if fabric && >=1.21 {
/*import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
*///?}
//? if fabric && >=1.21 {
/*import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
//? if >=1.21 {
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
//?}
//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?}
//? if <26.1 {
/*import net.minecraft.resources.ResourceLocation;
*///?}
//? if >=1.21 {
import net.minecraft.server.network.ConfigurationTask;
//?}
//? if >=1.21 {
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
//?}
//? if neoforge {
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
//?}
//? if neoforge {
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
//?}
//? if neoforge {
import net.neoforged.neoforge.network.registration.HandlerThread;
//?}

import dev.golem.core.Ids;
import dev.golem.core.hook.Hook;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Immutable metadata tasks applied and acknowledged before modern clients enter play. */
public final class Configurations {

    private record TaskRegistration<P extends GolemPayload>(GolemCodec<P> codec, Supplier<P> snapshot) {}
    private static final List<TaskRegistration<?>> TASKS = new CopyOnWriteArrayList<>();
    private static final Map<GolemCodec<?>, BiConsumer<?, ClientSessions.Session>> CLIENTS = new ConcurrentHashMap<>();
    /** Successful metadata application; configuration application precedes its task acknowledgement. */
    public static final Hook<BiConsumer<GolemCodec<?>, ClientSessions.Session>> CLIENT_APPLIED = Hook.notifying();

    private Configurations() {}

    public static <P extends GolemPayload> void declareTask(GolemCodec<P> codec, Supplier<P> snapshot) {
        if (isTaskDeclared(codec)) throw new IllegalStateException("Configuration task already declared: " + codec.id());
        TASKS.add(new TaskRegistration<>(codec, snapshot));
        GolemNetwork.declarePlayToClient(codec);
    }

    public static boolean isTaskDeclared(GolemCodec<?> codec) {
        return TASKS.stream().anyMatch(task -> task.codec().id().equals(codec.id()));
    }

    public static <P extends GolemPayload> void registerClient(GolemCodec<P> codec,
            BiConsumer<P, ClientSessions.Session> handler) {
        if (CLIENTS.putIfAbsent(codec, handler) != null) {
            throw new IllegalStateException("Configuration client already registered: " + codec.id());
        }
        GolemNetwork.registerPlayToClient(codec, (payload, context) -> apply(codec, payload, context.session()));
    }

    @SuppressWarnings("unchecked")
    private static <P extends GolemPayload> boolean apply(GolemCodec<P> codec, P payload, ClientSessions.Session session) {
        if (session == null || !session.active()) return false;
        var handler = (BiConsumer<P, ClientSessions.Session>) CLIENTS.get(codec);
        if (handler == null) throw new IllegalStateException("No client for configuration task: " + codec.id());
        handler.accept(payload, session);
        if (!session.active()) return false;
        for (var listener : CLIENT_APPLIED) listener.accept(codec, session);
        return true;
    }

    //? if >=1.20.5 {
    private record Ack(String task) implements GolemPayload {
        static final GolemCodec<Ack> CODEC = GolemCodec.of(Ack.class, Ids.of("golemcore", "configuration_ack"),
                (payload, buffer) -> buffer.writeUtf(payload.task()), buffer -> new Ack(buffer.readUtf()));
        @Override
        public
                //? if >=26.1 {
                Identifier
                //?} else {
                /*ResourceLocation
                *///?}
                id() { return CODEC.id(); }
    }

    private record Task<P extends GolemPayload>(GolemCodec<P> codec, Supplier<P> snapshot)
            implements ConfigurationTask {
        @Override
        public void start(Consumer<Packet<?>> sender) {
            sender.accept(new ClientboundCustomPayloadPacket(snapshot.get()));
        }
        @Override
        public ConfigurationTask.Type type() {
            return new ConfigurationTask.Type(codec.id().toString());
        }
    }
    //?}

    //? if neoforge && >=1.20.5 {
    static void flushNeoForge(RegisterPayloadHandlersEvent event) {
        event.registrar("golemcore").executesOn(HandlerThread.NETWORK)
                .configurationToServer(Ack.CODEC.type(), Ack.CODEC.streamCodec(), (payload, context) ->
                        context.enqueueWork(() -> context.finishCurrentTask(
                                new ConfigurationTask.Type(payload.task()))));
        for (var task : TASKS) registerNeoForge(event, task);
    }

    private static <P extends GolemPayload> void registerNeoForge(
            RegisterPayloadHandlersEvent event, TaskRegistration<P> task) {
        var codec = task.codec();
        event.registrar(codec.id().getNamespace()).executesOn(HandlerThread.NETWORK)
                .configurationToClient(codec.type(), codec.streamCodec(), (payload, context) -> {
                    var session = ClientSessions.current(context.connection());
                    if (apply(codec, payload, session)) context.reply(new Ack(codec.id().toString()));
                });
    }

    public static void registerTasks(RegisterConfigurationTasksEvent event) {
        for (var task : TASKS) {
            if (!event.getListener().hasChannel(task.codec().type())) {
                event.getListener().disconnect(Component.literal(
                        "Missing required configuration channel " + task.codec().id()));
                return;
            }
            addNeoForgeTask(event, task);
        }
    }

    private static <P extends GolemPayload> void addNeoForgeTask(
            RegisterConfigurationTasksEvent event, TaskRegistration<P> task) {
        event.register(new Task<>(task.codec(), task.snapshot()));
    }
    //?}

    //? if fabric && >=1.20.5 {
    /*private static boolean fabricInstalled;
    private static boolean fabricClientInstalled;

    static synchronized void flushFabric() {
        if (fabricInstalled) return;
        fabricInstalled = true;
        PayloadTypeRegistry.serverboundConfiguration()
                .register(Ack.CODEC.type(), Ack.CODEC.streamCodec());
        ServerConfigurationNetworking.registerGlobalReceiver(Ack.CODEC.type(),
                (payload, configurationContext) -> configurationContext.server().execute(() ->
                        ((FabricServerConfigurationPacketListenerImpl) (Object) configurationContext.packetListener())
                                .completeTask(new ConfigurationTask.Type(payload.task()))));
        for (var task : TASKS) registerFabric(task);
        ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
            for (var task : TASKS) {
                if (!ServerConfigurationNetworking.canSend(listener, task.codec().type())) {
                    listener.disconnect(Component.literal(
                            "Missing required configuration channel " + task.codec().id()));
                    return;
                }
                addFabricTask(listener, task);
            }
        });
    }

    private static <P extends GolemPayload> void registerFabric(TaskRegistration<P> task) {
        PayloadTypeRegistry.clientboundConfiguration()
                .register(task.codec().type(), task.codec().streamCodec());
    }

    private static <P extends GolemPayload> void addFabricTask(
            ServerConfigurationPacketListenerImpl listener, TaskRegistration<P> task) {
        ((FabricServerConfigurationPacketListenerImpl) (Object) listener)
                .addTask(new Task<>(task.codec(), task.snapshot()));
    }

    static synchronized void installFabricClient() {
        if (fabricClientInstalled) return;
        fabricClientInstalled = true;
        for (var task : TASKS) installFabricClient(task);
    }

    private static <P extends GolemPayload> void installFabricClient(TaskRegistration<P> task) {
        var codec = task.codec();
        ClientConfigurationNetworking.registerGlobalReceiver(codec.type(),
                (payload, configurationContext) -> {
                    var session = ClientSessions.forListener(configurationContext.packetListener());
                    if (apply(codec, payload, session)) configurationContext.responseSender().sendPacket(new Ack(codec.id().toString()));
                });
    }
    *///?}
}
