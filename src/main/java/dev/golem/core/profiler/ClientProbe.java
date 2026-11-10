package dev.golem.core.profiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.golem.core.client.ClientTicks;
import dev.golem.core.client.ShaderPacks;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * Client frames: time between frames, CPU time inside the frame, and CPU and GPU time per render pass. Each
 * loader render-stage event drops a CPU timestamp and a GPU timestamp query; the GPU results are read a few frames
 * late so the CPU never waits on them. A pass is named after the stage event that ends it. On Fabric the frame is
 * the level render, the only per-frame span its API offers. Client only.
 */
final class ClientProbe implements Profiler.Probe {

    private static final int FRAME_SLOTS = 6;
    private static final int MAX_MARKS = 24;
    private static @Nullable ClientProbe active;
    private static boolean hooked;
    private static int shown = -1;

    private final TimeSeries frames = new TimeSeries();
    private final TimeSeries cpu = new TimeSeries();
    private final TimeSeries gpu = new TimeSeries();
    private final Map<String, double[]> passes = new LinkedHashMap<>();
    private final Map<String, TimeSeries> passCpu = new HashMap<>();
    private final Map<String, TimeSeries> passGpu = new HashMap<>();
    private final JsonObject setup = new JsonObject();
    private final Slot[] slots = new Slot[FRAME_SLOTS];
    private @Nullable Slot frame;
    private int next;
    private long lastStart;
    private boolean gpuFailed;
    private boolean queriesMade;

    ClientProbe() {
        for (int i = 0; i < FRAME_SLOTS; i++) slots[i] = new Slot();
    }

    /** One frame's marks. Pass stats are {@code [frames, cpuSum, cpuMax, gpuFrames, gpuSum, gpuMax]}. */
    private static final class Slot {
        final String[] labels = new String[MAX_MARKS];
        final long[] cpu = new long[MAX_MARKS];
        final int[] queries = new int[MAX_MARKS];
        int marks;
        float time;
        boolean pending;
    }

    static void hook() {
        if (hooked) return;
        hooked = true;
        ClientTicks.onEndTick(ClientProbe::showCountdown);
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) -> begin());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) -> end("GUI, HUD and screen"));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.TickEvent.RenderTickEvent event) -> {
                    if (event.phase == net.minecraftforge.event.TickEvent.Phase.START) begin();
                    else end("GUI, HUD and screen");
                });
        *///?} elif fabric && >=26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.START_MAIN.register(context -> begin());
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AFTER_OPAQUE_TERRAIN
                .register(context -> mark("setup, sky and opaque terrain"));
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AFTER_SOLID_FEATURES
                .register(context -> mark("opaque entities, block entities, items"));
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES
                .register(context -> mark("translucent entities, block entities, items"));
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN
                .register(context -> mark("translucent terrain"));
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.END_MAIN
                .register(context -> end("particles, weather and rest of level"));
        *///?} elif fabric {
        /*net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(context -> begin());
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_SETUP.register(context -> mark("setup and culling"));
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.BEFORE_ENTITIES
                .register(context -> mark("sky and opaque terrain"));
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(context -> mark("entities"));
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.BEFORE_DEBUG_RENDER
                .register(context -> mark("block entities"));
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_TRANSLUCENT
                .register(context -> mark("translucent terrain"));
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST.register(context -> mark("particles and weather"));
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(context -> end("rest of level"));
        *///?}
        //? if neoforge && >=26.1 {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterSky event) -> mark("setup and sky"));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterOpaqueBlocks event) -> mark("opaque terrain"));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterOpaqueFeatures event) ->
                        mark("opaque entities, block entities, items"));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterTranslucentFeatures event) ->
                        mark("translucent entities, block entities, items"));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterTranslucentBlocks event) ->
                        mark("translucent terrain"));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterTranslucentParticles event) ->
                        mark("particles"));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterWeather event) -> mark("weather"));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterLevel event) -> mark("rest of level"));
        //?} elif neoforge {
        /*net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent event) -> mark(legacyStage(event.getStage())));
        *///?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.RenderLevelStageEvent event) -> mark(legacyStage(event.getStage())));
        *///?}
    }

    //? if neoforge && <26.1 {
    /*private static String legacyStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage stage) {
        return legacyStage(stage.toString());
    }
    *///?} elif forge {
    /*private static String legacyStage(net.minecraftforge.client.event.RenderLevelStageEvent.Stage stage) {
        return legacyStage(stage.toString());
    }
    *///?}

    //? if forgelike && <26.1 {
    /*private static String legacyStage(String id) {
        String name = id.substring(id.indexOf(':') + 1);
        return LEGACY_STAGES.getOrDefault(name, name);
    }
    *///?}

    // The Forge-era stage names; a stage absent here is reported under its own name.
    private static final Map<String, String> LEGACY_STAGES = Map.ofEntries(
            Map.entry("after_sky", "setup and sky"),
            Map.entry("after_solid_blocks", "solid terrain"),
            Map.entry("after_cutout_mipped_blocks", "cutout mipped terrain"),
            Map.entry("after_cutout_blocks", "cutout terrain"),
            Map.entry("after_entities", "entities"),
            Map.entry("after_block_entities", "block entities"),
            Map.entry("after_translucent_blocks", "translucent terrain"),
            Map.entry("after_tripwire_blocks", "tripwire"),
            Map.entry("after_particles", "particles"),
            Map.entry("after_weather", "weather"),
            Map.entry("after_level", "rest of level"));

    private static void showCountdown() {
        if (active == null) {
            shown = -1;
            return;
        }
        int left = Profiler.secondsLeft();
        if (left == shown) return;
        shown = left;
        Minecraft.getInstance().gui.setOverlayMessage(ProfilerCommands.countdown(left), false);
    }

    private static void begin() {
        ClientProbe probe = active;
        if (probe != null) probe.onBegin();
    }

    private static void mark(String label) {
        ClientProbe probe = active;
        if (probe != null) probe.onMark(label);
    }

    private static void end(String label) {
        ClientProbe probe = active;
        if (probe != null) probe.onEnd(label);
    }

    @Override
    public String key() {
        return "client";
    }

    /** Runs on the render thread (the command's thread), so it may read GL strings. */
    @Override
    public void start() {
        hook();
        readSetup();
        active = this;
    }

    @Override
    public void stop() {
        if (active == this) active = null;
    }

    @Override
    public CompletableFuture<JsonElement> collect() {
        return Minecraft.getInstance().submit(() -> {
            readQueries(true);
            deleteQueries();
            JsonObject json = new JsonObject();
            json.addProperty("frameScope", fabricScope() ? "level render" : "whole frame");
            json.add("setup", setup);
            json.add("frames", frames.toJson());
            json.add("cpu", cpu.toJson());
            json.add("gpu", gpu.toJson());
            if (gpuFailed) json.addProperty("gpuError", "timestamp queries unavailable");
            json.add("passes", passJson());
            json.add("passSeries", passSeriesJson());
            json.add("census", census());
            return json;
        });
    }

    @Override
    public Profiler.Spikes spikes() {
        double median = frames.percentile(0.5);
        double threshold = Math.max(median * 2, median + 10);
        return new Profiler.Spikes("Render thread", "frames over " + Math.round(threshold) + " ms", threshold,
                frames.windowsOver(threshold));
    }

    private void onBegin() {
        long now = System.nanoTime();
        if (lastStart != 0) frames.add(Profiler.seconds(now), (now - lastStart) / 1e6);
        lastStart = now;
        if (!gpuFailed) {
            try {
                makeQueries();
                readQueries(false);
            } catch (RuntimeException | LinkageError unsupported) {
                // A context without ARB_timer_query: the report shows CPU times only.
                gpuFailed = true;
            }
        }
        Slot slot = slots[next];
        // A slot whose results never arrived is reused; that frame's GPU times are dropped, not waited for.
        slot.pending = false;
        slot.marks = 0;
        slot.time = (float) Profiler.seconds(now);
        frame = slot;
        onMark("start");
    }

    private void onMark(String label) {
        Slot slot = frame;
        if (slot == null || slot.marks == MAX_MARKS) return;
        slot.labels[slot.marks] = label;
        slot.cpu[slot.marks] = System.nanoTime();
        if (!gpuFailed && queriesMade) GL33.glQueryCounter(slot.queries[slot.marks], GL33.GL_TIMESTAMP);
        slot.marks++;
    }

    private void onEnd(String label) {
        Slot slot = frame;
        if (slot == null) return;
        onMark(label);
        frame = null;
        long total = slot.cpu[slot.marks - 1] - slot.cpu[0];
        cpu.add(slot.time, total / 1e6);
        for (int i = 1; i < slot.marks; i++) {
            double[] pass = passes.computeIfAbsent(slot.labels[i], k -> new double[6]);
            double ms = (slot.cpu[i] - slot.cpu[i - 1]) / 1e6;
            pass[0]++;
            pass[1] += ms;
            pass[2] = Math.max(pass[2], ms);
            passCpu.computeIfAbsent(slot.labels[i], k -> new TimeSeries()).add(slot.time, ms);
        }
        slot.pending = !gpuFailed && queriesMade;
        next = (next + 1) % FRAME_SLOTS;
    }

    private void makeQueries() {
        if (queriesMade) return;
        for (Slot slot : slots) GL15.glGenQueries(slot.queries);
        queriesMade = true;
    }

    private void readQueries(boolean all) {
        if (!queriesMade) return;
        for (Slot slot : slots) {
            if (!slot.pending || slot.marks < 2) continue;
            int last = slot.queries[slot.marks - 1];
            if (!all && GL15.glGetQueryObjecti(last, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) continue;
            long previous = GL33.glGetQueryObjecti64(slot.queries[0], GL15.GL_QUERY_RESULT);
            long first = previous;
            for (int i = 1; i < slot.marks; i++) {
                long time = GL33.glGetQueryObjecti64(slot.queries[i], GL15.GL_QUERY_RESULT);
                double[] pass = passes.computeIfAbsent(slot.labels[i], k -> new double[6]);
                double ms = (time - previous) / 1e6;
                pass[3]++;
                pass[4] += ms;
                pass[5] = Math.max(pass[5], ms);
                passGpu.computeIfAbsent(slot.labels[i], k -> new TimeSeries()).add(slot.time, ms);
                previous = time;
            }
            gpu.add(slot.time, (previous - first) / 1e6);
            slot.pending = false;
        }
    }

    private void deleteQueries() {
        if (!queriesMade) return;
        for (Slot slot : slots) GL15.glDeleteQueries(slot.queries);
        queriesMade = false;
    }

    /** {@code [pass, frames, cpuMeanMs, cpuMaxMs, gpuMeanMs, gpuMaxMs]} in frame order. */
    private JsonArray passJson() {
        JsonArray rows = new JsonArray();
        passes.forEach((label, pass) -> {
            JsonArray row = new JsonArray();
            row.add(label);
            row.add(pass[0]);
            row.add(pass[0] == 0 ? 0 : pass[1] / pass[0]);
            row.add(pass[2]);
            row.add(pass[3] == 0 ? 0 : pass[4] / pass[3]);
            row.add(pass[5]);
            rows.add(row);
        });
        return rows;
    }

    /** Each pass's CPU and GPU time per frame, for the timeline. */
    private JsonArray passSeriesJson() {
        JsonArray rows = new JsonArray();
        for (String label : passes.keySet()) {
            JsonObject row = new JsonObject();
            row.addProperty("pass", label);
            row.add("cpu", passCpu.getOrDefault(label, new TimeSeries()).toJson());
            row.add("gpu", passGpu.getOrDefault(label, new TimeSeries()).toJson());
            rows.add(row);
        }
        return rows;
    }

    private void readSetup() {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            setup.addProperty("gpu", GL11.glGetString(GL11.GL_RENDERER));
            setup.addProperty("gpuVendor", GL11.glGetString(GL11.GL_VENDOR));
            setup.addProperty("glVersion", GL11.glGetString(GL11.GL_VERSION));
        } catch (RuntimeException | LinkageError noContext) {
            // Not a GL context on this thread; the report leaves the GPU name out.
            setup.addProperty("gpu", "unknown");
        }
        setup.addProperty("resolution", minecraft.getWindow().getWidth() + "x" + minecraft.getWindow().getHeight());
        setup.addProperty("renderDistance", minecraft.options.renderDistance().get());
        setup.addProperty("simulationDistance", minecraft.options.simulationDistance().get());
        setup.addProperty("maxFps", minecraft.options.framerateLimit().get());
        setup.addProperty("vsync", minecraft.options.enableVsync().get());
        setup.addProperty("guiScale", minecraft.options.guiScale().get());
        setup.addProperty("shaderPack", ShaderPacks.inUse());
        setup.addProperty("singleplayer", minecraft.getSingleplayerServer() != null);
    }

    private JsonObject census() {
        JsonObject json = new JsonObject();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return json;
        Map<String, Integer> entities = new HashMap<>();
        int entityCount = 0;
        for (Entity entity : level.entitiesForRendering()) {
            entityCount++;
            entities.merge(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())), 1, Integer::sum);
        }
        json.addProperty("entityCount", entityCount);
        json.add("entities", Census.sorted(entities));
        Map<String, Integer> all = new HashMap<>();
        Map<String, Integer> near = new HashMap<>();
        BlockPos camera = minecraft.player != null ? minecraft.player.blockPosition() : BlockPos.ZERO;
        int radius = minecraft.options.getEffectiveRenderDistance();
        int chunks = 0;
        for (int x = (camera.getX() >> 4) - radius; x <= (camera.getX() >> 4) + radius; x++) {
            for (int z = (camera.getZ() >> 4) - radius; z <= (camera.getZ() >> 4) + radius; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                chunks++;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    String type = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()));
                    all.merge(type, 1, Integer::sum);
                    if (be.getBlockPos().distSqr(camera) <= 64 * 64) near.merge(type, 1, Integer::sum);
                }
            }
        }
        json.addProperty("loadedChunks", chunks);
        json.add("blockEntities", Census.sorted(all));
        json.add("blockEntitiesWithin64", Census.sorted(near));
        return json;
    }

    private static boolean fabricScope() {
        //? if fabric {
        /*return true;
        *///?} else {
        return false;
        //?}
    }
}
