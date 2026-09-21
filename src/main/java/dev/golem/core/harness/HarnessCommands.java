package dev.golem.core.harness;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.golem.core.client.ContainerClicks;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * The command table. A handler returns a {@link JsonObject} (immediate reply), a
 * {@link HarnessServer.Pending} (re-polled each tick), or null (plain {@code ok}).
 *
 * <p>All handlers run on the client thread.
 */
final class HarnessCommands {

    @FunctionalInterface
    interface Handler {
        Object handle(Minecraft minecraft, JsonObject req) throws Exception;
    }

    private static final Map<String, Handler> handlers = new LinkedHashMap<>();

    private HarnessCommands() {}

    static Object dispatch(Minecraft minecraft, String name, JsonObject req) throws Exception {
        Handler handler = handlers.get(name);
        if (handler == null) return Json.error("unknown command '" + name + "'; try 'help'");
        return handler.handle(minecraft, req);
    }

    static void registerDefaults() {
        handlers.put("ping", (mc, req) -> {
            JsonObject json = Json.ok();
            json.addProperty("tick", Harness.tick());
            return json;
        });
        handlers.put("help", (mc, req) -> {
            JsonObject json = Json.ok();
            JsonArray names = new JsonArray();
            handlers.keySet().forEach(names::add);
            json.add("commands", names);
            JsonArray kinds = new JsonArray();
            for (String kind : new String[] {"status", "player", "inventory", "block", "block_entity", "entities", "screen", "log"}) kinds.add(kind);
            HarnessQueryRegistry.kinds().forEach(kinds::add);
            json.add("queries", kinds);
            return json;
        });
        handlers.put("status", (mc, req) -> HarnessQueries.status(mc));
        handlers.put("query", HarnessCommands::query);

        handlers.put("cmd", (mc, req) -> {
            if (mc.player == null) return Json.error("no player");
            String text = Json.str(req, "text", "");
            if (text.startsWith("/")) text = text.substring(1);
            mc.player.connection.sendCommand(text);
            return null;
        });
        handlers.put("chat", (mc, req) -> {
            if (mc.player == null) return Json.error("no player");
            mc.player.connection.sendChat(Json.str(req, "text", ""));
            return null;
        });

        handlers.put("key_down", (mc, req) -> {
            HarnessInput.press(mc, HarnessInput.resolve(mc, Json.str(req, "key", "")), true, Json.integer(req, "mods", 0));
            return null;
        });
        handlers.put("key_up", (mc, req) -> {
            HarnessInput.press(mc, HarnessInput.resolve(mc, Json.str(req, "key", "")), false, Json.integer(req, "mods", 0));
            return null;
        });
        handlers.put("key", (mc, req) -> {
            HarnessInput.Binding binding = HarnessInput.resolve(mc, Json.str(req, "key", ""));
            int hold = Json.integer(req, "hold_ticks", 1);
            HarnessInput.tap(mc, binding, hold, Json.integer(req, "mods", 0));
            long until = Harness.tick() + hold;
            return (HarnessServer.Pending) m -> Harness.tick() >= until ? Json.ok() : null;
        });
        handlers.put("type", (mc, req) -> {
            String text = Json.str(req, "text", "");
            text.codePoints().forEach(cp -> HarnessInput.charTyped(mc, cp, 0));
            return null;
        });
        handlers.put("release_all", (mc, req) -> {
            HarnessInput.releaseAll(mc);
            return null;
        });
        handlers.put("mouse_move", (mc, req) -> {
            double[] r = HarnessInput.mouseMove(mc, Json.number(req, "x", 0), Json.number(req, "y", 0));
            JsonObject json = Json.ok();
            json.addProperty("pixel_x", r[0]);
            json.addProperty("pixel_y", r[1]);
            json.addProperty("raw_x", r[2]);
            json.addProperty("raw_y", r[3]);
            return json;
        });
        handlers.put("scroll", (mc, req) -> {
            HarnessInput.scroll(mc, Json.number(req, "dx", 0), Json.number(req, "dy", 0));
            return null;
        });
        handlers.put("click", (mc, req) -> {
            int button = Json.integer(req, "button", 0);
            if (req.has("x") && req.has("y")) HarnessInput.mouseMove(mc, req.get("x").getAsDouble(), req.get("y").getAsDouble());
            HarnessInput.Binding binding = new HarnessInput.Binding(true, button);
            int hold = Json.integer(req, "hold_ticks", 1);
            HarnessInput.tap(mc, binding, hold, Json.integer(req, "mods", 0));
            long until = Harness.tick() + hold;
            return (HarnessServer.Pending) m -> Harness.tick() >= until ? Json.ok() : null;
        });
        handlers.put("use", (mc, req) -> hold(mc, "key.use", Json.integer(req, "hold_ticks", 1)));
        handlers.put("attack", (mc, req) -> hold(mc, "key.attack", Json.integer(req, "hold_ticks", 1)));
        handlers.put("hotbar", (mc, req) -> {
            int slot = Json.integer(req, "slot", 1);
            if (slot < 1 || slot > 9) return Json.error("slot must be 1..9");
            HarnessInput.tap(mc, HarnessInput.resolve(mc, "key.hotbar." + slot), 1, 0);
            return null;
        });
        handlers.put("slot_click", (mc, req) -> {
            if (mc.player == null || mc.gameMode == null) return Json.error("no player");
            var menu = mc.player.containerMenu;
            int slot = Json.integer(req, "slot", 0);
            int button = Json.integer(req, "button", 0);
            ContainerClicks.Action action = ContainerClicks.Action.valueOf(Json.str(req, "action", "PICKUP").toUpperCase(java.util.Locale.ROOT));
            ContainerClicks.send(mc.gameMode, menu.containerId, slot, button, action, mc.player);
            return null;
        });

        handlers.put("look", (mc, req) -> {
            if (mc.player == null) return Json.error("no player");
            float yaw = (float) Json.number(req, "yaw", mc.player.getYRot());
            float pitch = (float) Json.number(req, "pitch", mc.player.getXRot());
            setRotation(mc, yaw, pitch);
            return null;
        });
        handlers.put("look_at", (mc, req) -> {
            if (mc.player == null) return Json.error("no player");
            double tx = Json.number(req, "x", 0);
            double ty = Json.number(req, "y", 0);
            double tz = Json.number(req, "z", 0);
            if (Json.bool(req, "block", true) && !req.has("exact")) {
                tx = Math.floor(tx) + 0.5;
                ty = Math.floor(ty) + 0.5;
                tz = Math.floor(tz) + 0.5;
            }
            Vec3 eye = mc.player.getEyePosition();
            double dx = tx - eye.x;
            double dy = ty - eye.y;
            double dz = tz - eye.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
            setRotation(mc, yaw, pitch);
            JsonObject json = Json.ok();
            json.addProperty("yaw", yaw);
            json.addProperty("pitch", pitch);
            return json;
        });

        handlers.put("wait_ticks", (mc, req) -> {
            long until = Harness.tick() + Json.integer(req, "ticks", 1);
            return (HarnessServer.Pending) m -> Harness.tick() >= until ? Json.ok() : null;
        });
        handlers.put("wait_screen", (mc, req) -> {
            String contains = Json.str(req, "contains", "none");
            return (HarnessServer.Pending) m -> {
                boolean none = contains.equalsIgnoreCase("none");
                if (none ? m.screen == null : m.screen != null && (m.screen.getClass().getName().contains(contains)
                        || m.screen.getTitle().getString().contains(contains))) {
                    return HarnessQueries.screen(m);
                }
                return null;
            };
        });
        handlers.put("wait_in_world", (mc, req) -> HarnessWorld.untilInWorld(Harness.tick()));

        handlers.put("screenshot", (mc, req) -> {
            String path = Json.str(req, "path", "");
            if (path.isEmpty()) return Json.error("path required");
            return HarnessScreenshot.request(Path.of(path), Json.integer(req, "skip_frames", 1));
        });

        handlers.put("leave_world", (mc, req) -> {
            HarnessWorld.leave(mc);
            long start = Harness.tick();
            return (HarnessServer.Pending) m -> m.level == null && m.getOverlay() == null
                    && Harness.tick() > start + 2 ? Json.ok() : null;
        });
        handlers.put("join_world", (mc, req) -> {
            if (mc.level != null) return Json.error("already in a world; leave_world first");
            HarnessWorld.join(mc, Json.str(req, "name", ""));
            return HarnessWorld.untilInWorld(Harness.tick());
        });
        handlers.put("create_world", (mc, req) -> {
            if (mc.level != null) return Json.error("already in a world; leave_world first");
            return HarnessWorld.create(mc, Json.str(req, "name", "harness"));
        });

        handlers.put("set_option", (mc, req) -> setOption(mc, Json.str(req, "key", ""), Json.str(req, "value", "")));
        handlers.put("window", (mc, req) -> {
            if (req.has("minimized")) HarnessWindow.setMinimized(mc, req.get("minimized").getAsBoolean());
            JsonObject json = Json.ok();
            json.addProperty("focused", mc.isWindowActive());
            return json;
        });
        handlers.put("log", (mc, req) -> HarnessLog.tail(Json.integer(req, "lines", 50), Json.str(req, "filter", "")));
        handlers.put("teleport", (mc, req) -> {
            if (mc.player == null) return Json.error("no player");
            mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %s %s %s",
                    Json.number(req, "x", mc.player.getX()), Json.number(req, "y", mc.player.getY()), Json.number(req, "z", mc.player.getZ())));
            BlockPos target = new BlockPos((int) Math.floor(Json.number(req, "x", 0)), (int) Math.floor(Json.number(req, "y", 0)), (int) Math.floor(Json.number(req, "z", 0)));
            long start = Harness.tick();
            return (HarnessServer.Pending) m -> {
                if (m.player == null) return Json.error("no player");
                boolean arrived = m.player.blockPosition().distManhattan(target) <= 2 && m.level.hasChunkAt(m.player.blockPosition());
                return arrived && Harness.tick() > start ? Json.ok() : null;
            };
        });
        handlers.put("stop", (mc, req) -> {
            mc.stop();
            return null;
        });
    }

    private static Object hold(Minecraft mc, String mapping, int ticks) {
        HarnessInput.tap(mc, HarnessInput.resolve(mc, mapping), ticks, 0);
        long until = Harness.tick() + ticks;
        return (HarnessServer.Pending) m -> Harness.tick() >= until ? Json.ok() : null;
    }

    private static void setRotation(Minecraft mc, float yaw, float pitch) {
        pitch = Math.max(-90F, Math.min(90F, pitch));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        mc.player.setYHeadRot(yaw);
        mc.player.yBodyRot = yaw;
    }

    private static Object query(Minecraft mc, JsonObject req) {
        String kind = Json.str(req, "kind", "status");
        switch (kind) {
            case "status": return HarnessQueries.status(mc);
            case "player": return HarnessQueries.player(mc);
            case "inventory": return HarnessQueries.inventory(mc);
            case "block": return HarnessQueries.block(mc, req);
            case "block_entity": return HarnessQueries.blockEntity(mc, req);
            case "entities": return HarnessQueries.entities(mc, req);
            case "screen": return HarnessQueries.screen(mc);
            case "log": return HarnessLog.tail(Json.integer(req, "lines", 50), Json.str(req, "filter", ""));
            default:
                var custom = HarnessQueryRegistry.get(kind);
                if (custom == null) return Json.error("unknown query kind " + kind);
                return custom.apply(mc, req);
        }
    }

    private static Object setOption(Minecraft mc, String key, String value) {
        switch (key) {
            case "gui_scale":
                mc.options.guiScale().set(Integer.parseInt(value));
                //? if <26.1 {
                /*mc.resizeDisplay();
                *///?}
                break;
            case "render_distance":
                mc.options.renderDistance().set(Integer.parseInt(value));
                break;
            case "fov":
                mc.options.fov().set(Integer.parseInt(value));
                break;
            case "pause_on_lost_focus":
                mc.options.pauseOnLostFocus = Boolean.parseBoolean(value);
                break;
            case "auto_unpause":
                HarnessWindow.autoUnpause = Boolean.parseBoolean(value);
                break;
            case "hide_gui":
                mc.options.hideGui = Boolean.parseBoolean(value);
                break;
            case "camera":
                mc.options.setCameraType(net.minecraft.client.CameraType.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
                break;
            default:
                return Json.error("unknown option " + key);
        }
        return null;
    }
}
