package dev.golem.core.harness;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.golem.core.mixin.HarnessContainerScreenAccessor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Structured JSON views of client (and, for world data, integrated-server) state. */
final class HarnessQueries {

    private HarnessQueries() {}

    static JsonObject status(Minecraft minecraft) {
        JsonObject json = Json.ok();
        json.addProperty("tick", Harness.tick());
        json.addProperty("in_world", HarnessWorld.inWorld(minecraft));
        json.addProperty("fps", minecraft.getFps());
        json.addProperty("paused", minecraft.isPaused());
        json.addProperty("screen", minecraft.screen == null ? null : minecraft.screen.getClass().getName());
        json.addProperty("overlay", minecraft.getOverlay() == null ? null : minecraft.getOverlay().getClass().getName());
        Runtime runtime = Runtime.getRuntime();
        json.addProperty("heap_used_mb", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        json.addProperty("heap_max_mb", runtime.maxMemory() / (1024 * 1024));
        json.addProperty("gui_scale", minecraft.getWindow().getGuiScale());
        json.addProperty("gui_width", minecraft.getWindow().getGuiScaledWidth());
        json.addProperty("gui_height", minecraft.getWindow().getGuiScaledHeight());
        json.addProperty("window_width", minecraft.getWindow().getScreenWidth());
        json.addProperty("window_height", minecraft.getWindow().getScreenHeight());
        json.addProperty("focused", minecraft.isWindowActive());
        json.addProperty("singleplayer", minecraft.hasSingleplayerServer());
        if (minecraft.level != null) {
            json.addProperty("dimension", dimensionId(minecraft.level));
            json.addProperty("game_time", minecraft.level.getGameTime());
        }
        if (minecraft.player != null) json.add("player", playerSummary(minecraft.player));
        double[] cursor = HarnessInput.cursorGui(minecraft);
        json.addProperty("cursor_x", cursor[0]);
        json.addProperty("cursor_y", cursor[1]);
        json.addProperty("cursor_raw_x", minecraft.mouseHandler.xpos());
        json.addProperty("cursor_raw_y", minecraft.mouseHandler.ypos());
        return json;
    }

    private static String dimensionId(Level level) {
        return dev.golem.core.Ids.keyId(level.dimension()).toString();
    }

    private static JsonObject playerSummary(LocalPlayer player) {
        JsonObject p = new JsonObject();
        p.addProperty("name", player.getName().getString());
        p.addProperty("x", player.getX());
        p.addProperty("y", player.getY());
        p.addProperty("z", player.getZ());
        p.addProperty("yaw", player.getYRot());
        p.addProperty("pitch", player.getXRot());
        p.addProperty("health", player.getHealth());
        p.addProperty("food", player.getFoodData().getFoodLevel());
        p.addProperty("on_ground", player.onGround());
        p.addProperty("sneaking", player.isShiftKeyDown());
        p.add("main_hand", stack(player.getMainHandItem()));
        p.add("off_hand", stack(player.getOffhandItem()));
        return p;
    }

    static JsonObject player(Minecraft minecraft) {
        if (minecraft.player == null) return Json.error("no player");
        JsonObject json = Json.ok();
        JsonObject p = playerSummary(minecraft.player);
        p.addProperty("game_mode", minecraft.gameMode == null ? null : minecraft.gameMode.getPlayerMode().name());
        p.addProperty("block_x", minecraft.player.blockPosition().getX());
        p.addProperty("block_y", minecraft.player.blockPosition().getY());
        p.addProperty("block_z", minecraft.player.blockPosition().getZ());
        p.addProperty("eye_y", minecraft.player.getEyeY());
        p.addProperty("flying", minecraft.player.getAbilities().flying);
        p.addProperty("air", minecraft.player.getAirSupply());
        p.addProperty("xp_level", minecraft.player.experienceLevel);
        JsonArray effects = new JsonArray();
        minecraft.player.getActiveEffects().forEach(effect ->
                effects.add(effect.toString()));
        p.add("effects", effects);
        json.add("player", p);
        return json;
    }

    static JsonObject inventory(Minecraft minecraft) {
        if (minecraft.player == null) return Json.error("no player");
        JsonObject json = Json.ok();
        JsonArray slots = new JsonArray();
        var inventory = minecraft.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item.isEmpty()) continue;
            JsonObject s = stack(item);
            s.addProperty("slot", i);
            slots.add(s);
        }
        json.add("slots", slots);
        //? if >=26.1 {
        json.addProperty("selected", inventory.getSelectedSlot());
        //?} else {
        /*json.addProperty("selected", inventory.selected);
        *///?}
        json.addProperty("size", inventory.getContainerSize());
        if (minecraft.player.containerMenu != null) {
            json.add("carried", stack(minecraft.player.containerMenu.getCarried()));
        }
        return json;
    }

    static JsonObject stack(ItemStack stack) {
        JsonObject s = new JsonObject();
        if (stack == null || stack.isEmpty()) {
            s.addProperty("empty", true);
            return s;
        }
        s.addProperty("empty", false);
        s.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        s.addProperty("count", stack.getCount());
        s.addProperty("name", stack.getHoverName().getString());
        s.addProperty("damage", stack.getDamageValue());
        return s;
    }

    /** Runs {@code supplier} on the integrated server thread when {@code server} is requested and present. */
    private static JsonObject onSide(Minecraft minecraft, boolean server, Supplier<JsonObject> clientSide,
                                     java.util.function.Function<Level, JsonObject> serverSide) {
        MinecraftServer integrated = minecraft.getSingleplayerServer();
        if (!server || integrated == null || minecraft.level == null) return clientSide.get();
        var key = minecraft.level.dimension();
        try {
            return integrated.submit(() -> {
                Level level = integrated.getLevel(key);
                return level == null ? Json.error("server level missing") : serverSide.apply(level);
            }).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return Json.error("server query failed: " + e);
        }
    }

    static JsonObject block(Minecraft minecraft, JsonObject req) {
        BlockPos pos = pos(req);
        boolean server = Json.bool(req, "server", true);
        return onSide(minecraft, server,
                () -> minecraft.level == null ? Json.error("no level") : blockView(minecraft.level, pos, false),
                level -> blockView(level, pos, true));
    }

    static JsonObject blockEntity(Minecraft minecraft, JsonObject req) {
        BlockPos pos = pos(req);
        boolean server = Json.bool(req, "server", true);
        return onSide(minecraft, server,
                () -> minecraft.level == null ? Json.error("no level") : blockEntityView(minecraft.level, pos, false),
                level -> blockEntityView(level, pos, true));
    }

    private static JsonObject blockView(Level level, BlockPos pos, boolean server) {
        JsonObject json = Json.ok();
        json.addProperty("side", server ? "server" : "client");
        json.addProperty("loaded", level.hasChunkAt(pos));
        BlockState state = level.getBlockState(pos);
        json.addProperty("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        json.addProperty("state", state.toString());
        JsonObject props = new JsonObject();
        state.getProperties().forEach(property -> props.addProperty(property.getName(), String.valueOf(state.getValue(property))));
        json.add("properties", props);
        BlockEntity be = level.getBlockEntity(pos);
        json.addProperty("block_entity", be == null ? null : be.getClass().getName());
        json.addProperty("light", level.getMaxLocalRawBrightness(pos));
        return json;
    }

    private static JsonObject blockEntityView(Level level, BlockPos pos, boolean server) {
        JsonObject json = blockView(level, pos, server);
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            json.addProperty("nbt", (String) null);
            return json;
        }
        //? if >=1.21 {
        CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
        //?} else {
        /*CompoundTag tag = be.saveWithoutMetadata();
        *///?}
        json.addProperty("nbt", new SnbtPrinterTagVisitor().visit(tag));
        return json;
    }

    /**
     * What drew each declared machine: the instancing engine's report, and per position whether an
     * instanced visual owns it and how many vanilla submits replayed or skipped its declared model.
     *
     * <p>{@code trace:true} (re)starts the per-position counts; {@code positions} is an array of
     * {@code [x,y,z]}. This is the positive signal a fallback test needs — pixels cannot tell an
     * instanced draw from a vanilla one, but a replay count that rises while {@code owned} is false
     * says the vanilla renderer is the one drawing.
     */
    static JsonObject gem(Minecraft minecraft, JsonObject req) {
        if (req.has("trace")) dev.golem.core.render.gem.GemBER.trace(Json.bool(req, "trace", true));
        var report = dev.golem.core.render.Instancing.report();
        JsonObject json = Json.ok();
        json.addProperty("available", dev.golem.core.render.Instancing.isAvailable());
        json.addProperty("visualizing", minecraft.level != null
                && dev.golem.core.render.Instancing.isVisualizing(minecraft.level));
        json.addProperty("backend", report.backend());
        json.addProperty("visuals", report.visuals());
        json.addProperty("healthy", report.healthy());
        json.addProperty("instances", report.instances());
        json.addProperty("owning", report.owning());
        json.addProperty("draws", report.draws());
        com.google.gson.JsonArray out = new com.google.gson.JsonArray();
        java.util.List<BlockPos> wanted = new java.util.ArrayList<>();
        if (req.has("positions") && req.get("positions").isJsonArray()) {
            for (var element : req.getAsJsonArray("positions")) {
                var xyz = element.getAsJsonArray();
                wanted.add(new BlockPos(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt()));
            }
        } else if (Json.bool(req, "all", false)) {
            wanted.addAll(dev.golem.core.render.gem.GemBER.tracedPositions());
        }
        for (BlockPos pos : wanted) {
            int[] counts = dev.golem.core.render.gem.GemBER.traced(pos);
            JsonObject entry = new JsonObject();
            entry.addProperty("x", pos.getX());
            entry.addProperty("y", pos.getY());
            entry.addProperty("z", pos.getZ());
            entry.addProperty("owned", dev.golem.core.render.Visuals.owns(pos));
            BlockEntity be = minecraft.level == null ? null : minecraft.level.getBlockEntity(pos);
            entry.addProperty("block_entity", be == null ? null
                    : String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType())));
            entry.addProperty("replayed", counts[0]);
            entry.addProperty("skipped", counts[1]);
            out.add(entry);
        }
        json.add("positions", out);
        return json;
    }

    static BlockPos pos(JsonObject req) {
        return new BlockPos(Json.integer(req, "x", 0), Json.integer(req, "y", 0), Json.integer(req, "z", 0));
    }

    /**
     * Entities near the player. {@code server:true} reads them on the integrated server thread instead of the client
     * level, so positions are authoritative and {@code game_time} is the server tick they belong to (a client-side
     * position is interpolated and the client's tick count drifts against the server's).
     */
    static JsonObject entities(Minecraft minecraft, JsonObject req) {
        if (minecraft.level == null || minecraft.player == null) return Json.error("no level");
        double radius = Json.number(req, "radius", 32);
        String filter = Json.str(req, "type", "");
        boolean server = Json.bool(req, "server", false);
        java.util.UUID self = minecraft.player.getUUID();
        return onSide(minecraft, server,
                () -> entitiesView(minecraft.level, minecraft.level.entitiesForRendering(), minecraft.player, radius, filter, false),
                level -> {
                    Entity player = level.getPlayerByUUID(self);
                    if (player == null) return Json.error("server player missing");
                    return entitiesView(level, ((net.minecraft.server.level.ServerLevel) level).getAllEntities(), player,
                            radius, filter, true);
                });
    }

    private static JsonObject entitiesView(Level level, Iterable<Entity> entities, Entity player, double radius,
                                           String filter, boolean server) {
        JsonArray list = new JsonArray();
        for (Entity entity : entities) {
            if (entity == player) continue;
            if (entity.distanceTo(player) > radius) continue;
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            if (!filter.isEmpty() && !type.contains(filter)) continue;
            JsonObject e = new JsonObject();
            e.addProperty("id", entity.getId());
            e.addProperty("type", type);
            e.addProperty("name", entity.getName().getString());
            e.addProperty("x", entity.getX());
            e.addProperty("y", entity.getY());
            e.addProperty("z", entity.getZ());
            e.addProperty("alive", entity.isAlive());
            e.addProperty("distance", entity.distanceTo(player));
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                // by description id: the saved form differs per era and loader (1.20.1 stores a numeric Id, and only
                // Forge adds a named "forge:id"), and a mob's effects reach the client only as particles, so
                // read these with server:true
                JsonArray effects = new JsonArray();
                for (net.minecraft.world.effect.MobEffectInstance effect : living.getActiveEffects()) {
                    JsonObject fx = new JsonObject();
                    fx.addProperty("id", effect.getDescriptionId());
                    fx.addProperty("amplifier", effect.getAmplifier());
                    fx.addProperty("duration", effect.getDuration());
                    effects.add(fx);
                }
                e.add("effects", effects);
                // a hit leaves health, hurt time and knockback motion behind (read with server:true)
                e.addProperty("health", living.getHealth());
                e.addProperty("max_health", living.getMaxHealth());
                e.addProperty("hurt_time", living.hurtTime);
            }
            net.minecraft.world.phys.Vec3 motion = entity.getDeltaMovement();
            e.addProperty("vx", motion.x);
            e.addProperty("vy", motion.y);
            e.addProperty("vz", motion.z);
            list.add(e);
        }
        JsonObject json = Json.ok();
        json.addProperty("side", server ? "server" : "client");
        json.addProperty("game_time", level.getGameTime());
        json.add("entities", list);
        return json;
    }

    static JsonObject screen(Minecraft minecraft) {
        JsonObject json = Json.ok();
        Screen screen = minecraft.screen;
        json.addProperty("open", screen != null);
        if (screen == null) return json;
        json.addProperty("class", screen.getClass().getName());
        json.addProperty("title", screen.getTitle().getString());
        json.addProperty("pause_screen", screen.isPauseScreen());
        GuiEventListener focused = screen.getFocused();
        json.addProperty("focused", focused == null ? null : focused.getClass().getName());
        if (screen instanceof AbstractContainerScreen<?> container) {
            HarnessContainerScreenAccessor access = (HarnessContainerScreenAccessor) container;
            AbstractContainerMenu menu = container.getMenu();
            json.addProperty("menu", menu.getClass().getName());
            json.addProperty("container_id", menu.containerId);
            json.addProperty("left", access.golem$leftPos());
            json.addProperty("top", access.golem$topPos());
            json.addProperty("image_width", access.golem$imageWidth());
            json.addProperty("image_height", access.golem$imageHeight());
            Slot hovered = access.golem$hoveredSlot();
            json.addProperty("hovered_slot", hovered == null ? null : hovered.index);
            json.add("carried", stack(menu.getCarried()));
            JsonArray slots = new JsonArray();
            for (Slot slot : menu.slots) {
                JsonObject s = stack(slot.getItem());
                s.addProperty("index", slot.index);
                s.addProperty("x", access.golem$leftPos() + slot.x);
                s.addProperty("y", access.golem$topPos() + slot.y);
                s.addProperty("center_x", access.golem$leftPos() + slot.x + 8);
                s.addProperty("center_y", access.golem$topPos() + slot.y + 8);
                s.addProperty("container", slot.container.getClass().getSimpleName());
                s.addProperty("player_inventory", slot.container instanceof net.minecraft.world.entity.player.Inventory);
                slots.add(s);
            }
            json.add("slots", slots);
        }
        JsonArray widgets = new JsonArray();
        walk(screen, widgets, 0);
        json.add("widgets", widgets);
        return json;
    }

    private static void walk(ContainerEventHandler handler, JsonArray out, int depth) {
        if (depth > 8) return;
        for (GuiEventListener child : handler.children()) {
            if (child instanceof AbstractWidget widget) {
                JsonObject w = new JsonObject();
                w.addProperty("class", widget.getClass().getName());
                w.addProperty("x", widget.getX());
                w.addProperty("y", widget.getY());
                w.addProperty("width", widget.getWidth());
                w.addProperty("height", widget.getHeight());
                w.addProperty("center_x", widget.getX() + widget.getWidth() / 2);
                w.addProperty("center_y", widget.getY() + widget.getHeight() / 2);
                w.addProperty("message", widget.getMessage().getString());
                w.addProperty("active", widget.active);
                w.addProperty("visible", widget.visible);
                w.addProperty("focused", widget.isFocused());
                w.addProperty("hovered", widget.isHovered());
                if (widget instanceof EditBox box) w.addProperty("value", box.getValue());
                out.add(w);
            } else if (child != null) {
                JsonObject w = new JsonObject();
                w.addProperty("class", child.getClass().getName());
                w.addProperty("focused", child.isFocused());
                out.add(w);
            }
            if (child instanceof ContainerEventHandler nested) walk(nested, out, depth + 1);
        }
    }
}
