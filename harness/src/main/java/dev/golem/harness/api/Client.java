package dev.golem.harness.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * One connection to a running harness client. Every method is one request and one reply; a reply
 * with {@code ok:false} becomes a {@link HarnessException}.
 *
 * <p>Coordinates for {@link #click} and {@link #mouseMove} are GUI-scaled, the same space the
 * {@link #screen} query reports slot and widget rectangles in.
 */
public final class Client implements AutoCloseable {

    private final String node;
    private final int port;
    private Socket socket;
    private BufferedReader in;
    private Writer out;
    private Path screenshotDir = Path.of("out", "adhoc");
    private final List<String> screenshots = new ArrayList<>();
    private final List<String> transcript = new ArrayList<>();
    public boolean echo = false;

    private Client(String node, int port) {
        this.node = node;
        this.port = port;
    }

    public static Client connect(String node, int port) {
        Client client = new Client(node, port);
        client.open();
        return client;
    }

    private void open() {
        IOException last = null;
        // The game JVM may prefer IPv6 (ModDev passes -Djava.net.preferIPv6Addresses=system), so the
        // listener can sit on ::1 or 127.0.0.1; try both.
        for (String host : new String[] {"127.0.0.1", "::1"}) {
            try {
                socket = new Socket(InetAddress.getByName(host), port);
                last = null;
                break;
            } catch (IOException e) {
                last = e;
            }
        }
        try {
            if (last != null) throw last;
            socket.setSoTimeout(0);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new HarnessException(node + ": cannot connect to 127.0.0.1:" + port + " (" + e.getMessage() + ")", e);
        }
    }

    public String node() {
        return node;
    }

    public int port() {
        return port;
    }

    /** Where {@link #screenshot(String)} writes; set by the runner per node. */
    public void setScreenshotDir(Path dir) {
        this.screenshotDir = dir;
    }

    public Path screenshotDir() {
        return screenshotDir;
    }

    public List<String> screenshots() {
        return screenshots;
    }

    public List<String> transcript() {
        return transcript;
    }

    @Override
    public void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
            // closing
        }
    }

    // ---- transport -------------------------------------------------------------------------

    /** Sends a raw request; {@code cmd} is added from {@code command}. Throws on {@code ok:false}. */
    public synchronized JsonObject send(String command, JsonObject params, long timeoutMs) {
        JsonObject req = params == null ? new JsonObject() : params.deepCopy();
        req.addProperty("cmd", command);
        req.addProperty("timeout_ms", timeoutMs);
        if (!req.has("timeout_ticks")) req.addProperty("timeout_ticks", Math.max(1, timeoutMs / 50));
        String line = req.toString();
        String reply;
        try {
            out.write(line);
            out.write('\n');
            out.flush();
            socket.setSoTimeout((int) Math.min(Integer.MAX_VALUE, timeoutMs + 10_000L));
            reply = in.readLine();
        } catch (IOException e) {
            throw new HarnessException(node + ": transport failure on " + command + ": " + e.getMessage(), e);
        }
        if (reply == null) throw new HarnessException(node + ": connection closed during " + command);
        transcript.add("> " + line);
        transcript.add("< " + (reply.length() > 400 ? reply.substring(0, 400) + "..." : reply));
        if (echo) System.out.println("[" + node + "] " + command + " -> " + (reply.length() > 200 ? reply.substring(0, 200) + "..." : reply));
        JsonObject json = JsonParser.parseString(reply).getAsJsonObject();
        if (json.has("ok") && !json.get("ok").getAsBoolean()) {
            throw new HarnessException(node + ": " + command + " failed: " + (json.has("error") ? json.get("error").getAsString() : reply));
        }
        return json;
    }

    public JsonObject send(String command, JsonObject params) {
        return send(command, params, 60_000L);
    }

    public JsonObject send(String command) {
        return send(command, null, 60_000L);
    }

    /** Sends the raw JSON text of a request (must carry {@code cmd}). */
    public JsonObject raw(String json) {
        JsonObject req = JsonParser.parseString(json).getAsJsonObject();
        String command = req.get("cmd").getAsString();
        req.remove("cmd");
        long timeout = req.has("timeout_ms") ? req.get("timeout_ms").getAsLong() : 120_000L;
        return send(command, req, timeout);
    }

    private static JsonObject obj(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            String k = (String) kv[i];
            if (v == null) continue;
            if (v instanceof Number n) o.addProperty(k, n);
            else if (v instanceof Boolean b) o.addProperty(k, b);
            else o.addProperty(k, v.toString());
        }
        return o;
    }

    // ---- state -----------------------------------------------------------------------------

    public JsonObject ping() {
        return send("ping", null, 10_000L);
    }

    public JsonObject status() {
        return send("status", null, 10_000L);
    }

    public boolean inWorld() {
        return status().get("in_world").getAsBoolean();
    }

    public JsonObject query(String kind, JsonObject params) {
        JsonObject p = params == null ? new JsonObject() : params.deepCopy();
        p.addProperty("kind", kind);
        return send("query", p, 30_000L);
    }

    public JsonObject player() {
        return query("player", null).getAsJsonObject("player");
    }

    public JsonObject inventory() {
        return query("inventory", null);
    }

    public JsonObject screen() {
        return query("screen", null);
    }

    public JsonObject block(int x, int y, int z) {
        return query("block", obj("x", x, "y", y, "z", z));
    }

    public JsonObject blockEntity(int x, int y, int z) {
        return query("block_entity", obj("x", x, "y", y, "z", z));
    }

    public JsonArray entities(double radius) {
        return query("entities", obj("radius", radius)).getAsJsonArray("entities");
    }

    public List<String> log(int lines, String filterRegex) {
        JsonArray array = query("log", obj("lines", lines, "filter", filterRegex)).getAsJsonArray("lines");
        List<String> out = new ArrayList<>();
        for (JsonElement e : array) out.add(e.getAsString());
        return out;
    }

    private long logMark = -1;

    /** Remembers the current end of the log; {@link #logSinceMark} reads only what came after. */
    public void markLog() {
        logMark = query("log", obj("lines", 0, "filter", "")).get("total").getAsLong();
    }

    /** Lines logged since {@link #markLog} (all lines when no mark was set), filtered by regex. */
    public List<String> logSinceMark(String filterRegex) {
        JsonObject all = query("log", obj("lines", 0, "filter", ""));
        long total = all.get("total").getAsLong();
        long since = logMark < 0 ? 4000 : Math.max(0, total - logMark);
        return log((int) Math.min(4000, since), filterRegex);
    }

    // ---- actions ---------------------------------------------------------------------------

    /** Runs a slash command as the player (leading slash optional). */
    public void cmd(String text) {
        send("cmd", obj("text", text));
    }

    public void chat(String text) {
        send("chat", obj("text", text));
    }

    /** Presses and releases a key: {@code key.use}, {@code w}, {@code space}, {@code f3}, {@code mouse.left}. */
    public void key(String key) {
        send("key", obj("key", key, "hold_ticks", 1));
    }

    public void key(String key, int holdTicks) {
        send("key", obj("key", key, "hold_ticks", holdTicks));
    }

    public void keyDown(String key) {
        send("key_down", obj("key", key));
    }

    public void keyUp(String key) {
        send("key_up", obj("key", key));
    }

    /** Types text into the focused widget, one character event per code point. */
    public void type(String text) {
        send("type", obj("text", text));
    }

    public void releaseAll() {
        send("release_all");
    }

    public void mouseMove(double guiX, double guiY) {
        send("mouse_move", obj("x", guiX, "y", guiY));
    }

    /** Left-click at GUI-scaled coordinates. */
    public void click(double guiX, double guiY) {
        click(guiX, guiY, 0);
    }

    public void click(double guiX, double guiY, int button) {
        send("click", obj("x", guiX, "y", guiY, "button", button, "hold_ticks", 1));
    }

    public void scroll(double dy) {
        send("scroll", obj("dx", 0, "dy", dy));
    }

    /** Right mouse button (use item / interact) held for {@code holdTicks}. */
    public void use(int holdTicks) {
        send("use", obj("hold_ticks", holdTicks));
    }

    public void use() {
        use(1);
    }

    public void attack(int holdTicks) {
        send("attack", obj("hold_ticks", holdTicks));
    }

    public void attack() {
        attack(1);
    }

    public void hotbar(int slot) {
        send("hotbar", obj("slot", slot));
    }

    /** Direct menu click (bypasses screen hit-testing): PICKUP, QUICK_MOVE, SWAP, CLONE, THROW. */
    public void slotClick(int slot, int button, String action) {
        send("slot_click", obj("slot", slot, "button", button, "action", action));
    }

    public void look(double yaw, double pitch) {
        send("look", obj("yaw", yaw, "pitch", pitch));
    }

    /** Aims at the centre of block {@code (x, y, z)}. */
    public void lookAt(int x, int y, int z) {
        send("look_at", obj("x", x, "y", y, "z", z));
    }

    /** Aims at an exact point. */
    public void lookAt(double x, double y, double z) {
        send("look_at", obj("x", x, "y", y, "z", z, "exact", true));
    }

    /**
     * Starts a smooth client-side camera sweep: from where the player is, along an arc round
     * {@code (cx, cz)} at the current height, looking at {@code (lx, ly, lz)}. The angle follows
     * {@code amplitudeDeg * sin(2 pi t / periodTicks)}, so it slows, turns and comes back. It moves once per
     * client tick and vanilla interpolates each frame. It keeps running in the background until
     * {@link #orbitStop()}, a teleport, a look or a world change.
     */
    public void orbit(double cx, double cz, double lx, double ly, double lz, double amplitudeDeg, int periodTicks) {
        send("orbit", obj("cx", cx, "cz", cz, "lx", lx, "ly", ly, "lz", lz,
                "amplitude", amplitudeDeg, "period", periodTicks));
    }

    public void orbitStop() {
        send("orbit_stop");
    }

    public void teleport(double x, double y, double z) {
        send("teleport", obj("x", x, "y", y, "z", z), 30_000L);
    }

    public void waitTicks(int ticks) {
        send("wait_ticks", obj("ticks", ticks), ticks * 50L + 30_000L);
    }

    /** Waits until a screen whose class name contains {@code contains} is open ("none" = no screen). */
    public JsonObject waitScreen(String contains, long timeoutMs) {
        return send("wait_screen", obj("contains", contains), timeoutMs);
    }

    public void waitInWorld(long timeoutMs) {
        send("wait_in_world", null, timeoutMs);
    }

    public void setOption(String key, String value) {
        send("set_option", obj("key", key, "value", value));
    }

    public void leaveWorld(long timeoutMs) {
        send("leave_world", null, timeoutMs);
    }

    public void joinWorld(String name, long timeoutMs) {
        send("join_world", obj("name", name), timeoutMs);
    }

    public void createWorld(String name, long timeoutMs) {
        send("create_world", obj("name", name), timeoutMs);
    }

    // ---- worlds: the default superflat world and the opt-in seeded world -----------------------

    /**
     * Seed of the opt-in seeded world ({@link #SEEDED_WORLD}, default world preset). Fixed forever:
     * worldgen tests rely on the same terrain, biomes and structure positions on every run.
     */
    public static final long SEEDED_WORLD_SEED = 4815162342L;
    /** Save name of the seeded world; it has its own snapshot, separate from the superflat world. */
    public static final String SEEDED_WORLD = "harness_seeded";
    /** Save name of the default superflat world (the CLI sets it from harness.json {@code world}). */
    public static volatile String flatWorld = "harness";

    /** Snapshot/restore of a save, installed by the CLI (the API has no file-system view of the run dir). */
    public interface WorldFiles {
        void snapshot(String node, String world) throws IOException;

        void restore(String node, String world) throws IOException;
    }

    public static volatile WorldFiles worldFiles;

    /** Creates a world: {@code type} "flat" (superflat) or "seeded" (default preset with {@code seed}). */
    public void createWorld(String name, String type, long seed, long timeoutMs) {
        send("create_world", obj("name", name, "type", type, "seed", seed), timeoutMs);
    }

    /** Save folder of the open world (null on the title screen). */
    public String currentWorld() {
        JsonElement current = send("world_info", null).get("current");
        return current == null || current.isJsonNull() ? null : current.getAsString();
    }

    public boolean worldExists(String name) {
        return send("world_info", obj("name", name)).get("exists").getAsBoolean();
    }

    /**
     * Freezes the environment the way the flat-world fixtures do: creative, peaceful, day, clear
     * weather, no daylight/weather cycle, no mob spawning. Both gamerule spellings are sent: 26.1
     * renamed them (advance_time, advance_weather, spawn_mobs); the unknown one is only a chat error.
     */
    public void freezeWorld() {
        cmd("gamemode creative");
        cmd("difficulty peaceful");
        cmd("gamerule doDaylightCycle false");
        cmd("gamerule advance_time false");
        cmd("gamerule doWeatherCycle false");
        cmd("gamerule advance_weather false");
        cmd("gamerule doMobSpawning false");
        cmd("gamerule spawn_mobs false");
        cmd("time set 6000");
        cmd("weather clear");
        waitTicks(2);
    }

    /**
     * Switches this client to world {@code name}, creating it ({@code type} "flat" or "seeded") on first
     * use. A freshly created world is frozen ({@link #freezeWorld}) and snapshotted before it is used.
     */
    public void useWorld(String name, String type) {
        if (name.equals(currentWorld()) && inWorld()) return;
        if (status().get("in_world").getAsBoolean() || currentWorld() != null) leaveWorld(60_000L);
        if (worldExists(name)) {
            joinWorld(name, 240_000L);
            return;
        }
        createWorld(name, type, SEEDED_WORLD_SEED, 300_000L);
        freezeWorld();
        if (worldFiles != null) {
            leaveWorld(60_000L);
            try {
                worldFiles.snapshot(node, name);
            } catch (IOException e) {
                throw new HarnessException("snapshot of " + name + " failed: " + e.getMessage(), e);
            }
            joinWorld(name, 240_000L);
        }
    }

    /** The fixed-seed normal world ({@link #SEEDED_WORLD}, seed {@link #SEEDED_WORLD_SEED}). */
    public void useSeededWorld() {
        useWorld(SEEDED_WORLD, "seeded");
    }

    /** Back to the default superflat world. */
    public void useFlatWorld() {
        useWorld(flatWorld, "flat");
    }

    /** Leaves, restores the open world's snapshot, and rejoins (like {@code harness world reset}). */
    public void resetWorld() {
        String name = currentWorld();
        if (name == null) throw new HarnessException("resetWorld: not in a world");
        if (worldFiles == null) throw new HarnessException("resetWorld: no snapshot support (not run by the CLI)");
        leaveWorld(60_000L);
        try {
            worldFiles.restore(node, name);
        } catch (IOException e) {
            throw new HarnessException("restore of " + name + " failed: " + e.getMessage(), e);
        }
        joinWorld(name, 240_000L);
    }

    public void window(boolean minimized) {
        send("window", obj("minimized", minimized));
    }

    public void stop() {
        try {
            send("stop", null, 5_000L);
        } catch (HarnessException ignored) {
            // the client is going away
        }
    }

    // ---- screenshots -----------------------------------------------------------------------

    /** Captures the next frame to {@code <screenshotDir>/<name>.png} and returns the path. */
    public Path screenshot(String name) {
        return screenshot(name, null);
    }

    /**
     * Captures and optionally crops to a GUI-scaled rectangle {@code {x, y, w, h}}. The crop is done
     * here from the full frame, so the full frame is also kept as {@code <name>.full.png}.
     */
    public Path screenshot(String name, int[] guiCrop) {
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        Path target = screenshotDir.resolve(safe + (guiCrop == null ? ".png" : ".full.png")).toAbsolutePath();
        JsonObject reply = send("screenshot", obj("path", target.toString(), "skip_frames", 1), 30_000L);
        Path result = Path.of(reply.get("path").getAsString());
        if (guiCrop != null) {
            JsonObject status = status();
            double scale = status.get("window_width").getAsDouble() / status.get("gui_width").getAsDouble();
            Path cropped = screenshotDir.resolve(safe + ".png").toAbsolutePath();
            try {
                BufferedImage full = ImageIO.read(result.toFile());
                int x = (int) Math.round(guiCrop[0] * scale);
                int y = (int) Math.round(guiCrop[1] * scale);
                int w = (int) Math.round(guiCrop[2] * scale);
                int h = (int) Math.round(guiCrop[3] * scale);
                x = Math.max(0, Math.min(x, full.getWidth() - 1));
                y = Math.max(0, Math.min(y, full.getHeight() - 1));
                w = Math.max(1, Math.min(w, full.getWidth() - x));
                h = Math.max(1, Math.min(h, full.getHeight() - y));
                ImageIO.write(full.getSubimage(x, y, w, h), "png", cropped.toFile());
                result = cropped;
            } catch (IOException e) {
                throw new HarnessException(node + ": crop failed: " + e, e);
            }
        }
        screenshots.add(result.toString());
        return result;
    }

    // ---- helpers over queries ----------------------------------------------------------------

    /** The open screen's simple class name, or null. */
    public String screenClass() {
        JsonObject s = status();
        return s.get("screen").isJsonNull() ? null : s.get("screen").getAsString();
    }

    /** Finds a slot in the open container screen by index; null when absent. */
    public JsonObject slot(int index) {
        JsonObject screen = screen();
        if (!screen.has("slots")) return null;
        for (JsonElement e : screen.getAsJsonArray("slots")) {
            JsonObject s = e.getAsJsonObject();
            if (s.get("index").getAsInt() == index) return s;
        }
        return null;
    }

    /** Finds a widget whose message contains {@code text} (case-insensitive); null when absent. */
    public JsonObject widget(String text) {
        JsonObject screen = screen();
        if (!screen.has("widgets")) return null;
        String needle = text.toLowerCase(Locale.ROOT);
        for (JsonElement e : screen.getAsJsonArray("widgets")) {
            JsonObject w = e.getAsJsonObject();
            if (w.has("message") && w.get("message").getAsString().toLowerCase(Locale.ROOT).contains(needle)) return w;
        }
        return null;
    }

    /** Clicks the centre of a container slot through real mouse events. */
    public void clickSlot(int index) {
        clickSlot(index, 0);
    }

    public void clickSlot(int index, int button) {
        JsonObject s = slot(index);
        if (s == null) throw new HarnessException(node + ": no slot " + index + " on the open screen");
        click(s.get("center_x").getAsDouble(), s.get("center_y").getAsDouble(), button);
    }

    /** Clicks the centre of a widget found by its label. */
    public void clickWidget(String text) {
        JsonObject w = widget(text);
        if (w == null) throw new HarnessException(node + ": no widget with text '" + text + "' on the open screen");
        click(w.get("center_x").getAsDouble(), w.get("center_y").getAsDouble());
    }

    static String pretty(JsonElement e) {
        return e.toString();
    }

    public static Path ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new HarnessException("cannot create " + dir, e);
        }
        return dir;
    }
}
