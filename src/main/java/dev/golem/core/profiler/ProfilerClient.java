package dev.golem.core.profiler;

import com.mojang.brigadier.context.CommandContext;
import dev.golem.core.command.CompatCommands;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

/** The client half of {@code /golem profiler}. Client only. */
final class ProfilerClient {

    private ProfilerClient() {}

    static <S> int start(S source, CompatCommands.ClientFeedback<S> feedback, int seconds) {
        Minecraft minecraft = Minecraft.getInstance();
        List<Profiler.Probe> probes = new ArrayList<>();
        probes.add(new ClientProbe());
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null) probes.add(new ServerProbe(server, null));
        boolean started = Profiler.start(seconds, server != null ? "singleplayer" : "client", probes,
                new Profiler.Listener() {
                    @Override
                    public void finished(Path report) {
                        minecraft.execute(() -> feedback.success(source, Component.literal("[Golem] Profile saved: ")
                                .append(fileLink(report))
                                .append(Component.literal(" (click to open)").withStyle(ChatFormatting.GRAY))));
                    }

                    @Override
                    public void failed(String reason) {
                        minecraft.execute(() -> feedback.error(source,
                                Component.literal("[Golem] Profiling failed: " + reason)));
                    }
                });
        if (!started) {
            feedback.error(source, ProfilerCommands.busy());
            return 0;
        }
        feedback.success(source, Component.literal("[Golem] Profiling for " + seconds
                + " s. Play normally and look at what is slow; /golem profiler stop ends early."));
        return 1;
    }

    /** Sends {@code /golemserver profiler}: the loaders run {@code sendCommand} through the client tree first. */
    static <S> int forward(CommandContext<S> ctx, CompatCommands.ClientFeedback<S> feedback, int seconds) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            feedback.error(ctx.getSource(), Component.literal("Not connected to a server"));
            return 0;
        }
        connection.sendCommand(ProfilerCommands.SERVER_ROOT + " profiler " + seconds);
        return 1;
    }

    static <S> int openFolder(S source, CompatCommands.ClientFeedback<S> feedback) {
        Path dir = Profiler.directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException unwritable) {
            feedback.error(source, Component.literal("[Golem] Cannot create " + dir));
            return 0;
        }
        //? if >=26.1 {
        net.minecraft.util.Util.getPlatform().openPath(dir);
        //?} elif >=1.21 {
        /*net.minecraft.Util.getPlatform().openPath(dir);
        *///?} else {
        /*net.minecraft.Util.getPlatform().openFile(dir.toFile());
        *///?}
        return 1;
    }

    private static MutableComponent fileLink(Path report) {
        String name = report.getFileName().toString();
        //? if >=1.21.5 {
        ClickEvent click = new ClickEvent.OpenFile(report.toAbsolutePath());
        //?} else {
        /*ClickEvent click = new ClickEvent(ClickEvent.Action.OPEN_FILE, report.toAbsolutePath().toString());
        *///?}
        return Component.literal(name).withStyle(style -> style.withClickEvent(click)
                .withUnderlined(true).withColor(ChatFormatting.AQUA));
    }
}
