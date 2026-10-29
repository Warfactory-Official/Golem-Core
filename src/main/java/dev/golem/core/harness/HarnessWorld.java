package dev.golem.core.harness;

import com.google.gson.JsonObject;
import dev.golem.core.GolemCore;
import dev.golem.core.harness.mixin.HarnessCreateWorldScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Open, create and leave singleplayer worlds without the vanilla menus.
 */
final class HarnessWorld {

    private HarnessWorld() {
    }

    /**
     * In a world with a player, no loading overlay and no loading screen, and the local chunk present.
     */
    static boolean inWorld(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.getOverlay() != null) return false;
        if (minecraft.screen != null) {
            String name = minecraft.screen.getClass().getSimpleName();
            if (name.contains("Loading") || name.contains("Receiving") || name.contains("Progress")
                    || name.contains("Connect")) return false;
        }
        return minecraft.level.hasChunkAt(minecraft.player.blockPosition()) && minecraft.player.tickCount > 5;
    }

    static void leave(Minecraft minecraft) {
        if (minecraft.level == null) return;
        //? if >=26.1 {
        minecraft.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
        //?} elif >=1.21 {
        /*minecraft.level.disconnect();
        minecraft.disconnect();
        minecraft.setScreen(new TitleScreen());
        *///?} else {
        /*minecraft.level.disconnect();
        minecraft.clearLevel();
        minecraft.setScreen(new TitleScreen());
        *///?}
    }

    static void join(Minecraft minecraft, String name) {
        //? if >=1.21 {
        minecraft.createWorldOpenFlows().openWorld(name, () -> minecraft.setScreen(new TitleScreen()));
        //?} else {
        /*minecraft.createWorldOpenFlows().loadLevel(new TitleScreen(), name);
         *///?}
    }

    /**
     * Connects to a dedicated server through vanilla's connect screen.
     */
    static void joinServer(Minecraft minecraft, String host, int port) {
        String address = host + ":" + port;
        //? if >=1.21 {
        ServerData data = new ServerData("harness", address, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address), data, false,
                null);
        //?} else {
        /*ServerData data = new ServerData("harness", address, false);
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address), data, false);
        *///?}
    }

    /**
     * Pending that resolves once the client is in a world (or errors when it returns to a menu).
     */
    static HarnessServer.Pending untilInWorld(long startNanos) {
        return minecraft -> {
            if (inWorld(minecraft)) return Json.ok();
            if (System.nanoTime() - startNanos > 2_000_000_000L && minecraft.level == null && minecraft.getOverlay() == null
                    && minecraft.screen instanceof TitleScreen) {
                return Json.error("returned to the title screen without joining");
            }
            return null;
        };
    }

    /**
     * Save folder name of the open singleplayer world, or null.
     */
    static String currentSave(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) return null;
        java.nio.file.Path root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                        .toAbsolutePath().normalize();
        return root.getFileName() == null ? null : root.getFileName().toString();
    }

    static boolean exists(Minecraft minecraft, String name) {
        return minecraft.getLevelSource().levelExists(name);
    }

    /**
     * Drives vanilla's world-creation UI state: creative, peaceful, commands on, then the screen's own
     * create action. {@code type} "flat" (default) picks the superflat preset; "seeded" picks the default
     * (normal) preset with the fixed {@code seed}. Resolves when the new world has loaded.
     */
    static HarnessServer.Pending create(Minecraft minecraft, String name) {
        return create(minecraft, name, "flat", 0L);
    }

    static HarnessServer.Pending create(Minecraft minecraft, String name, String type, long seed) {
        boolean seeded = "seeded".equals(type);
        long start = System.nanoTime();
        return new HarnessServer.Pending() {
            boolean opened;
            boolean submitted;

            @Override
            public JsonObject poll(Minecraft mc) {
                if (!opened) {
                    // Never start under a loading overlay: vanilla's world-load loop spins until the
                    // overlay is gone, and it cannot go while that loop holds the client thread.
                    if (mc.getOverlay() != null) return null;
                    opened = true;
                    //? if >=26.1 {
                    CreateWorldScreen.openFresh(mc, () -> mc.setScreen(new TitleScreen()));
                    //?} else {
                    /*CreateWorldScreen.openFresh(mc, new TitleScreen());
                     *///?}
                    return null;
                }
                if (!submitted) {
                    if (!(mc.screen instanceof CreateWorldScreen screen)) return null;
                    WorldCreationUiState state = screen.getUiState();
                    state.setName(name);
                    state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                    state.setDifficulty(Difficulty.PEACEFUL);
                    //? if >=1.21 {
                    state.setAllowCommands(true);
                    //?} else {
                    /*state.setAllowCheats(true);
                     *///?}
                    var wanted = seeded ? WorldPresets.NORMAL : WorldPresets.FLAT;
                    WorldCreationUiState.WorldTypeEntry preset = null;
                    for (WorldCreationUiState.WorldTypeEntry entry : state.getNormalPresetList()) {
                        if (entry.preset() != null && entry.preset().is(wanted)) preset = entry;
                    }
                    if (preset == null) {
                        for (WorldCreationUiState.WorldTypeEntry entry : state.getAltPresetList()) {
                            if (entry.preset() != null && entry.preset().is(wanted)) preset = entry;
                        }
                    }
                    if (preset == null) return Json.error(
                            "no " + (seeded ? "default" : "superflat") + " preset in the world-type list");
                    state.setWorldType(preset);
                    // The seed text is parsed like the vanilla field: a number is used as-is.
                    if (seeded) state.setSeed(Long.toString(seed));
                    GolemCore.LOG.info("[harness] creating world '{}' ({}, creative)", name,
                            seeded ? "default preset, seed " + seed : "superflat");
                    ((HarnessCreateWorldScreenAccessor) screen).golem$onCreate();
                    submitted = true;
                    return null;
                }
                return untilInWorld(start).poll(mc);
            }

            @Override
            public long timeoutMillis() {
                return 180_000L;
            }
        };
    }
}
