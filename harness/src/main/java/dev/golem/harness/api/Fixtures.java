package dev.golem.harness.api;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable environment builders. Layer 1 is slash commands; layer 2 places blocks through the
 * player's own hands so a mod's placement code runs; scenarios compose both.
 */
public final class Fixtures {

    private Fixtures() {
    }

    /**
     * Force-loads and builds a flat stone platform centred on {@code (x, y, z)} with {@code radius},
     * clears the air above it, sets creative, day, no weather, no mob spawning, and stands the player
     * on it facing +Z (yaw 0).
     */
    public static void platform(Client c, int x, int y, int z, int radius) {
        platform(c, x, y, z, radius, "minecraft:stone");
    }

    public static void platform(Client c, int x, int y, int z, int radius, String block) {
        int cx = x >> 4;
        int cz = z >> 4;
        int cr = (radius >> 4) + 1;
        c.cmd("forceload add " + ((cx - cr) << 4) + " " + ((cz - cr) << 4) + " " + ((cx + cr) << 4) + " " + ((cz + cr) << 4));
        c.cmd("gamemode creative");
        c.cmd("gamerule doDaylightCycle false");
        c.cmd("gamerule doWeatherCycle false");
        c.cmd("gamerule doMobSpawning false");
        // 26.1 renamed these three; the spelling a node does not know is only a chat error.
        c.cmd("gamerule advance_time false");
        c.cmd("gamerule advance_weather false");
        c.cmd("gamerule spawn_mobs false");
        c.cmd("gamerule sendCommandFeedback true");
        c.cmd("time set day");
        c.cmd("weather clear");
        c.cmd("kill @e[type=!player]");
        fill(c, x - radius, y, z - radius, x + radius, y, z + radius, block);
        fill(c, x - radius, y + 1, z - radius, x + radius, y + 8, z + radius, "minecraft:air");
        c.waitTicks(2);
        c.teleport(x + 0.5, y + 1, z + 0.5);
        c.look(0, 0);
        c.cmd("clear");
        c.waitTicks(2);
        Expect.block(c, x, y, z, block);
    }

    /**
     * Fills an ordered cuboid in batches within vanilla's default 32,768-block limit.
     */
    public static void fill(Client c, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        int width = x2 - x1 + 1, height = y2 - y1 + 1, depth = z2 - z1 + 1;
        if ((long) width * height * depth <= 32_768) {
            c.cmd("fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block);
        } else if (width >= height && width >= depth) {
            int middle = x1 + (x2 - x1) / 2;
            fill(c, x1, y1, z1, middle, y2, z2, block);
            fill(c, middle + 1, y1, z1, x2, y2, z2, block);
        } else if (height >= depth) {
            int middle = y1 + (y2 - y1) / 2;
            fill(c, x1, y1, z1, x2, middle, z2, block);
            fill(c, x1, middle + 1, z1, x2, y2, z2, block);
        } else {
            int middle = z1 + (z2 - z1) / 2;
            fill(c, x1, y1, z1, x2, y2, middle, block);
            fill(c, x1, y1, middle + 1, x2, y2, z2, block);
        }
    }

    /**
     * Waits for matching light and rebuilt sections; the first air sample must be fully sunlit.
     */
    public static void waitForSkylight(Client c, int[]... positions) {
        long deadline = System.nanoTime() + 30_000_000_000L;
        List<String> samples;
        boolean sectionsBuilt;
        do {
            samples = new ArrayList<>();
            boolean lit = true;
            for (int i = 0; i < positions.length; i++) {
                var position = new JsonObject();
                position.addProperty("x", positions[i][0]);
                position.addProperty("y", positions[i][1]);
                position.addProperty("z", positions[i][2]);
                position.addProperty("server", false);
                var client = c.query("block", position);
                position.addProperty("server", true);
                var server = c.query("block", position);
                samples.add(position + " client=" + client + " server=" + server);
                if (!client.get("loaded").getAsBoolean() || !server.get("loaded").getAsBoolean()) {
                    lit = false;
                    continue;
                }
                int clientLight = client.get("light").getAsInt(), serverLight = server.get("light").getAsInt();
                if (clientLight != serverLight || i == 0 && serverLight != 15) lit = false;
            }
            sectionsBuilt = c.status().get("sections_built").getAsBoolean();
            if (lit && sectionsBuilt) return;
            c.waitTicks(1);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Scene lighting not ready: sectionsBuilt=" + sectionsBuilt + ", samples=" + samples);
    }

    /**
     * Gives one stack and lets the inventory update reach the client.
     */
    public static void give(Client c, String itemId, int count) {
        c.cmd("give @s " + itemId + " " + count);
        c.waitTicks(2);
    }

    public static void giveOnly(Client c, String itemId, int count) {
        c.cmd("clear");
        c.waitTicks(1);
        give(c, itemId, count);
        c.hotbar(1);
        long deadline = System.nanoTime() + 5_000_000_000L;
        JsonObject held;
        do {
            held = c.player().getAsJsonObject("main_hand");
            if (!held.get("empty").getAsBoolean() && held.get("id").getAsString().equals(itemId)) return;
            c.waitTicks(1);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Client did not select " + count + " x " + itemId + ": " + held);
    }

    /**
     * Places {@code itemId} on top of block {@code (x, y, z)} by hand: the player stands two blocks
     * away on the same level, aims at the top face and right-clicks. Returns the placed block's view.
     */
    public static JsonObject placeOnTop(Client c, String itemId, int x, int y, int z) {
        giveOnly(c, itemId, 1);
        c.teleport(x + 2.5, y + 1, z + 0.5);
        c.waitTicks(2);
        // Aim just under the top surface so the ray enters through the top face.
        c.lookAt(x + 0.5, y + 0.98, z + 0.5);
        waitForTarget(c, x, y, z);
        c.use(2);
        c.waitTicks(3);
        return c.block(x, y + 1, z);
    }

    /**
     * Aims at block {@code (x, y, z)} from the current position and right-clicks it empty-handed:
     * hotbar slot 9 is selected first (keep it empty), so the rest of the inventory is untouched.
     */
    public static void interact(Client c, int x, int y, int z) {
        c.hotbar(9);
        c.waitTicks(1);
        c.lookAt(x, y, z);
        c.waitTicks(1);
        c.use(2);
        c.waitTicks(3);
    }

    /**
     * Waits for vanilla's crosshair pick to catch up with an aimed block.
     */
    public static void waitForTarget(Client c, int x, int y, int z) {
        long deadline = System.nanoTime() + 5_000_000_000L;
        JsonObject player;
        do {
            player = c.player();
            if (player.get("target").getAsString().equals("BLOCK")
                    && player.get("target_x").getAsInt() == x
                    && player.get("target_y").getAsInt() == y
                    && player.get("target_z").getAsInt() == z) return;
            c.waitTicks(1);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Crosshair did not reach " + x + "," + y + "," + z + ": " + player);
    }

    /**
     * Places a structure template published from {@code harness/fixtures/<name>.nbt}.
     */
    public static void template(Client c, String name, int x, int y, int z) {
        c.cmd("place template harness:" + name + " " + x + " " + y + " " + z);
        c.waitTicks(2);
    }

    public static void setBlock(Client c, int x, int y, int z, String block) {
        c.cmd("setblock " + x + " " + y + " " + z + " " + block);
        c.waitTicks(1);
    }
}
