package dev.golem.core.harness;

import com.google.gson.JsonObject;
import dev.golem.core.GolemCore;
import dev.golem.core.mixin.HarnessCreateWorldScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Open, create and leave singleplayer worlds without the vanilla menus. */
final class HarnessWorld {

    private HarnessWorld() {}

    /** In a world with a player, no loading overlay and no loading screen, and the local chunk present. */
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

    /** Pending that resolves once the client is in a world (or errors when it returns to a menu). */
    static HarnessServer.Pending untilInWorld(long startTick) {
        return minecraft -> {
            if (inWorld(minecraft)) return Json.ok();
            if (Harness.tick() - startTick > 40 && minecraft.level == null && minecraft.getOverlay() == null
                    && minecraft.screen instanceof TitleScreen) {
                return Json.error("returned to the title screen without joining");
            }
            return null;
        };
    }

    /**
     * Drives vanilla's world-creation UI state: superflat, creative, peaceful, commands on, then the
     * screen's own create action. Resolves when the new world has loaded.
     */
    static HarnessServer.Pending create(Minecraft minecraft, String name) {
        long start = Harness.tick();
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
                    WorldCreationUiState.WorldTypeEntry flat = null;
                    for (WorldCreationUiState.WorldTypeEntry entry : state.getNormalPresetList()) {
                        if (entry.preset() != null && entry.preset().is(WorldPresets.FLAT)) flat = entry;
                    }
                    if (flat == null) {
                        for (WorldCreationUiState.WorldTypeEntry entry : state.getAltPresetList()) {
                            if (entry.preset() != null && entry.preset().is(WorldPresets.FLAT)) flat = entry;
                        }
                    }
                    if (flat == null) return Json.error("no superflat preset in the world-type list");
                    state.setWorldType(flat);
                    GolemCore.LOG.info("[harness] creating world '{}' (superflat, creative)", name);
                    ((HarnessCreateWorldScreenAccessor) screen).golem$onCreate();
                    submitted = true;
                    return null;
                }
                return untilInWorld(start).poll(mc);
            }

            @Override
            public long timeoutTicks() {
                return 20L * 180L;
            }
        };
    }
}
