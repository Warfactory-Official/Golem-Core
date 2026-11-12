package dev.golem.core.profiler;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.golem.core.command.CompatCommands;
import dev.golem.core.command.Permissions;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /golem profiler [seconds]}, {@code /golem profiler stop} and {@code /golem profiler status}. The client
 * command profiles the client and, in singleplayer, its integrated server; {@code /golem profiler server [seconds]}
 * asks the connected server to run its {@code /golemserver profiler}, which writes the report on the server's disk.
 */
public final class ProfilerCommands {

    static final String SERVER_ROOT = "golemserver";

    private ProfilerCommands() {}

    /** Registers the server command and the tick hooks. Called once at mod construction on both sides. */
    public static void init() {
        ServerProbe.hook();
        CompatCommands.server(ProfilerCommands::registerServer);
    }

    /** {@code golem} on a console; {@code golemserver} from a client, whose own {@code /golem} tree would catch it. */
    private static void registerServer(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String root : new String[]{"golem", SERVER_ROOT}) registerServer(dispatcher, root);
    }

    private static void registerServer(CommandDispatcher<CommandSourceStack> dispatcher, String root) {
        dispatcher.register(Commands.literal(root).requires(Permissions.level(4))
                .then(Commands.literal("profiler")
                        .executes(ctx -> startServer(ctx.getSource(), Profiler.DEFAULT_SECONDS))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, Profiler.MAX_SECONDS))
                                .executes(ctx -> startServer(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds"))))
                        .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource()::sendFailure,
                                message -> ctx.getSource().sendSuccess(() -> message, true))))
                        .then(Commands.literal("status").executes(ctx -> {
                            ctx.getSource().sendSuccess(ProfilerCommands::status, false);
                            return 1;
                        }))));
    }

    private static int startServer(CommandSourceStack source, int seconds) {
        MinecraftServer server = source.getServer();
        Profiler.Listener listener = new Profiler.Listener() {
            @Override
            public void writing() {
                server.execute(() -> source.sendSuccess(() -> Component.literal(
                        "Capture finished, writing the report. Keep the server running until it is saved."), true));
            }

            @Override
            public void finished(Path report) {
                server.execute(() -> source.sendSuccess(() -> Component.literal("Profile saved to "
                        + Profiler.directory().getParent().relativize(report)), true));
            }

            @Override
            public void failed(String reason) {
                server.execute(() -> source.sendFailure(Component.literal("Profiling failed: " + reason)));
            }
        };
        boolean started = Profiler.start(seconds, "server", List.of(new ServerProbe(server, source.getPlayer())), listener);
        if (!started) {
            source.sendFailure(busy());
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Profiling the server for " + seconds + " s..."), true);
        return 1;
    }

    /** Registers the client command. Called once from client initialisation. */
    public static void initClient() {
        ClientProbe.hook();
        CompatCommands.client(new CompatCommands.ClientTree() {
            @Override
            public <S> void register(CommandDispatcher<S> dispatcher, CompatCommands.ClientFeedback<S> feedback) {
                dispatcher.register(LiteralArgumentBuilder.<S>literal("golem")
                        .then(LiteralArgumentBuilder.<S>literal("profiler")
                                .executes(ctx -> ProfilerClient.start(ctx.getSource(), feedback, Profiler.DEFAULT_SECONDS))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("seconds",
                                                IntegerArgumentType.integer(1, Profiler.MAX_SECONDS))
                                        .executes(ctx -> ProfilerClient.start(ctx.getSource(), feedback,
                                                IntegerArgumentType.getInteger(ctx, "seconds"))))
                                .then(LiteralArgumentBuilder.<S>literal("stop").executes(ctx -> stop(
                                        message -> feedback.error(ctx.getSource(), message),
                                        message -> feedback.success(ctx.getSource(), message))))
                                .then(LiteralArgumentBuilder.<S>literal("status").executes(ctx -> {
                                    feedback.success(ctx.getSource(), status());
                                    return 1;
                                }))
                                .then(LiteralArgumentBuilder.<S>literal("open").executes(ctx -> ProfilerClient.openFolder(ctx.getSource(), feedback)))
                                .then(LiteralArgumentBuilder.<S>literal("server")
                                        .executes(ctx -> ProfilerClient.forward(ctx, feedback, Profiler.DEFAULT_SECONDS))
                                        .then(RequiredArgumentBuilder.<S, Integer>argument("seconds",
                                                        IntegerArgumentType.integer(1, Profiler.MAX_SECONDS))
                                                .executes(ctx -> ProfilerClient.forward(ctx, feedback,
                                                        IntegerArgumentType.getInteger(ctx, "seconds")))))));
            }
        });
    }

    private static int stop(Consumer<Component> error, Consumer<Component> success) {
        if (!Profiler.stop()) {
            error.accept(Component.literal("No profiler capture is running"));
            return 0;
        }
        success.accept(Component.literal("Stopping the profiler; the report is being written"));
        return 1;
    }

    static Component status() {
        return Component.literal(Profiler.isRunning()
                ? "Profiler running, " + Profiler.secondsLeft() + " s left"
                : "No profiler capture is running. Reports are in " + Profiler.directory());
    }

    /** The action-bar countdown a capture shows to whoever started it. */
    static Component countdown(int secondsLeft) {
        return Component.literal("Profiling: " + secondsLeft + "s Left").withStyle(ChatFormatting.GOLD);
    }

    static Component writingNotice() {
        return Component.literal("Profiling: writing report").withStyle(ChatFormatting.GOLD);
    }

    static Component busy() {
        return Component.literal("A profiler capture is already running (" + Profiler.secondsLeft()
                + " s left); /golem profiler stop ends it").withStyle(ChatFormatting.RED);
    }
}
