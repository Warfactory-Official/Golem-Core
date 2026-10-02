package dev.golem.core.flywheel;

//? if >=26.1 {

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.golem.core.command.CompatCommands;
import dev.golem.core.flywheel.backend.Backend;
import dev.golem.core.flywheel.backend.BackendManager;
import dev.golem.core.flywheel.visual.VisualizationLifecycle;
import dev.golem.core.platform.Platform;
import java.io.IOException;
import net.minecraft.network.chat.Component;

/**
 * {@code /flywheel backend [DEFAULT|off|instancing|indirect]}: the published engine's client command,
 * for the vendored one.
 *
 * <p>Same spelling and same effect as the published {@code FlwCommands} on 1.20.1/1.21.1: the choice
 * is saved to the config file and applied at once. Switching off tears every visual down and hands
 * every machine back to its vanilla renderer on the next frame. Switching on rebuilds the visuals for
 * the loaded area — but only if the engine started with the game, because its pipelines are declared at
 * startup; an engine that was off then says so and asks for a restart.
 *
 * <p>Not registered when a mod with the published engine's id is present, so the two can never fight
 * over the literal.
 */
public final class FlywheelCommands {

    private static boolean registered;

    private FlywheelCommands() {}

    public static void register() {
        if (registered || !Platform.isClient() || Platform.isModLoaded("flywheel")) return;
        registered = true;
        CompatCommands.client(new CompatCommands.ClientTree() {
            @Override
            public <S> void register(CommandDispatcher<S> dispatcher, CompatCommands.ClientFeedback<S> feedback) {
                LiteralArgumentBuilder<S> backend = LiteralArgumentBuilder.<S>literal("backend")
                        .executes(context -> {
                            feedback.success(context.getSource(), Component.literal(describe()));
                            return 1;
                        });
                for (String value : FlywheelConfig.VALUES) {
                    backend.then(LiteralArgumentBuilder.<S>literal(value).executes(context -> {
                        Result result = apply(value);
                        if (result.ok()) feedback.success(context.getSource(), Component.literal(result.message()));
                        else feedback.error(context.getSource(), Component.literal(result.message()));
                        return result.ok() ? 1 : 0;
                    }));
                }
                dispatcher.register(LiteralArgumentBuilder.<S>literal("flywheel").then(backend));
            }
        });
    }

    /** What the engine is doing now, in one line. */
    public static String describe() {
        if (!Flywheel.isAvailable()) {
            return "Golem flywheel backend: off (set at startup; restart after changing "
                    + FlywheelConfig.FILE_NAME + ")";
        }
        String reason = EngineState.offReason();
        if (reason != null) return "Golem flywheel backend: off (" + reason + ")";
        Backend selected = BackendManager.selected();
        return "Golem flywheel backend: " + (selected == null ? "not chosen yet" : selected.name());
    }

    public record Result(boolean ok, String message) {}

    /** Saves {@code value} and applies it to the running engine. Client thread only. */
    public static Result apply(String value) {
        try {
            FlywheelConfig.write(value);
        } catch (IOException | RuntimeException failure) {
            return new Result(false, "Could not write " + FlywheelConfig.FILE_NAME + ": " + failure.getMessage());
        }
        String suffix = Flywheel.backendForcedExternally()
                ? " (note: -Dgolem.flywheel.backend / GOLEM_FLYWHEEL_BACKEND overrides the file at the next start)"
                : "";
        String normalised = FlywheelConfig.normalise(value);
        if (FlywheelConfig.isOff(normalised)) {
            EngineState.switchOff("switched off with /flywheel backend off");
            VisualizationLifecycle.teardownIfPending();
            return new Result(true, "Golem flywheel backend: off" + suffix);
        }
        if (!ClientFlywheel.initialized()) {
            return new Result(true, "Saved " + (normalised.isEmpty() ? FlywheelConfig.DEFAULT : normalised)
                    + "; the engine was off when the game started, so restart to turn it on" + suffix);
        }
        // Turning on and switching backend while running take the same stop as "off" first: settle the in-flight
        // frame plan, shut every manager down and free MeshPool's GPU buffers (which belong to the old backend),
        // so nothing carries over into the new one. switchOn then drops the teardown this already performed.
        if (!EngineState.failed()) {
            EngineState.switchOff("switching to " + (normalised.isEmpty() ? FlywheelConfig.DEFAULT : normalised));
            VisualizationLifecycle.teardownIfPending();
        }
        if (!EngineState.switchOn()) {
            return new Result(false, "The engine stopped itself on a failure this session (see the log); "
                    + "restart to try again" + suffix);
        }
        BackendManager.reset();
        VisualizationLifecycle.rebuildCurrent();
        return new Result(true, describe() + suffix);
    }
}
//?}
