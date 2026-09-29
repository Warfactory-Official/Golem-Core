package dev.golem.harness.api;

import com.google.gson.JsonObject;

/**
 * Reusable environment builders. Layer 1 is slash commands; layer 2 places blocks through the
 * player's own hands so a mod's placement code runs; scenarios compose both.
 */
public final class Fixtures {

    private Fixtures() {}

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
        c.cmd("fill " + (x - radius) + " " + y + " " + (z - radius) + " " + (x + radius) + " " + y + " " + (z + radius) + " " + block);
        c.cmd("fill " + (x - radius) + " " + (y + 1) + " " + (z - radius) + " " + (x + radius) + " " + (y + 8) + " " + (z + radius) + " minecraft:air");
        c.waitTicks(2);
        c.teleport(x + 0.5, y + 1, z + 0.5);
        c.look(0, 0);
        c.cmd("clear");
        c.waitTicks(2);
        Expect.block(c, x, y, z, block);
    }

    /** Clears the player's inventory and gives one stack. */
    public static void give(Client c, String itemId, int count) {
        c.cmd("give @s " + itemId + " " + count);
        c.waitTicks(2);
    }

    public static void giveOnly(Client c, String itemId, int count) {
        c.cmd("clear");
        c.waitTicks(1);
        give(c, itemId, count);
        c.hotbar(1);
        c.waitTicks(1);
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
        c.waitTicks(1);
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

    /** Places a structure template published from {@code harness/fixtures/<name>.nbt}. */
    public static void template(Client c, String name, int x, int y, int z) {
        c.cmd("place template harness:" + name + " " + x + " " + y + " " + z);
        c.waitTicks(2);
    }

    public static void setBlock(Client c, int x, int y, int z, String block) {
        c.cmd("setblock " + x + " " + y + " " + z + " " + block);
        c.waitTicks(1);
    }
}
