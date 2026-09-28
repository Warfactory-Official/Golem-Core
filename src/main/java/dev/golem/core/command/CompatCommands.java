package dev.golem.core.command;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * Server and client command registration, declared once for every loader.
 *
 * <p>Each loader has its own registration hook: Fabric's {@code CommandRegistrationCallback} and
 * {@code ClientCommandRegistrationCallback}, and the NeoForge/Forge {@code RegisterCommandsEvent} and
 * {@code RegisterClientCommandsEvent} on two different event buses. The server half is the same Brigadier
 * dispatcher everywhere. The client half is not: Fabric hands out a dispatcher over its own
 * {@code FabricClientCommandSource}, NeoForge and Forge one over a client-side {@code CommandSourceStack}.
 * A client command is therefore written against Brigadier's generic source type and reports through
 * {@link ClientFeedback}, which hides which of the two it got.
 *
 * <pre>{@code
 * CompatCommands.server(dispatcher -> dispatcher.register(Commands.literal("ntmrad")...));
 * CompatCommands.client(new CompatCommands.ClientTree() {
 *     public <S> void register(CommandDispatcher<S> dispatcher, CompatCommands.ClientFeedback<S> feedback) {
 *         dispatcher.register(LiteralArgumentBuilder.<S>literal("ntmclient")
 *                 .executes(ctx -> { feedback.success(ctx.getSource(), Component.literal("hi")); return 1; }));
 *     }
 * });
 * }</pre>
 *
 * <p>The name carries {@code Compat} because {@code net.minecraft.commands.Commands} already owns the bare one.
 */
public final class CompatCommands {

    private CompatCommands() {}

    /**
     * Registers server commands. Called at mod construction; {@code handler} then runs every time the
     * server (re)builds its dispatcher: world load and {@code /reload}.
     */
    public static void server(Consumer<CommandDispatcher<CommandSourceStack>> handler) {
        //? if fabric {
        /*net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> handler.accept(dispatcher));
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.RegisterCommandsEvent event) -> handler.accept(event.getDispatcher()));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.RegisterCommandsEvent event) -> handler.accept(event.getDispatcher()));
        *///?}
    }

    /**
     * Registers client-side commands, which run on the client without a round trip to the server. Must
     * only be called from client initialisation: the hooks it installs live in client-only classes.
     */
    public static void client(ClientTree tree) {
        //? if fabric {
        /*net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> tree.register(dispatcher,
                        new ClientFeedback<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>() {
                            @Override
                            public void success(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,
                                                Component message) {
                                source.sendFeedback(message);
                            }

                            @Override
                            public void error(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,
                                              Component message) {
                                source.sendError(message);
                            }
                        }));
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) ->
                        tree.register(event.getDispatcher(), STACK_FEEDBACK));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.RegisterClientCommandsEvent event) ->
                        tree.register(event.getDispatcher(), STACK_FEEDBACK));
        *///?}
    }

    /** A client command tree, written against Brigadier's source type parameter. */
    public interface ClientTree {
        <S> void register(CommandDispatcher<S> dispatcher, ClientFeedback<S> feedback);
    }

    /** Reports back to whoever ran a client command, whichever source type the loader uses. */
    public interface ClientFeedback<S> {
        void success(S source, Component message);

        void error(S source, Component message);
    }

    //? if forgelike {
    private static final ClientFeedback<CommandSourceStack> STACK_FEEDBACK = new ClientFeedback<>() {
        @Override public void success(CommandSourceStack source, Component message) {
            source.sendSuccess(() -> message, false);
        }

        @Override public void error(CommandSourceStack source, Component message) {
            source.sendFailure(message);
        }
    };
    //?}
}
