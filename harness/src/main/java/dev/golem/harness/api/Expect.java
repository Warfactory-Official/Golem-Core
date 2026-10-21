package dev.golem.harness.api;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Assertions that read like the report line they produce. Every failure is a {@link HarnessException}. */
public final class Expect {

    private Expect() {}

    public static void that(boolean condition, String message) {
        if (!condition) throw new HarnessException("expectation failed: " + message);
    }

    public static void equal(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new HarnessException("expectation failed: " + what + " expected <" + expected + "> but was <" + actual + ">");
        }
    }

    /** A screen whose class name or title contains {@code contains} is open. */
    public static JsonObject screenOpen(Client c, String contains) {
        JsonObject screen = c.screen();
        boolean open = screen.get("open").getAsBoolean();
        String cls = open ? screen.get("class").getAsString() : null;
        String title = open ? screen.get("title").getAsString() : null;
        that(open && (cls.contains(contains) || title.contains(contains)),
                "screen '" + contains + "' open, but screen is " + (open ? cls + " titled '" + title + "'" : "none"));
        return screen;
    }

    public static void noScreen(Client c) {
        String open = c.screenClass();
        that(open == null, "no screen open, but " + open + " is open");
    }

    /** The screen stays open across {@code ticks} ticks (catches a menu the server closes on tick 1). */
    public static JsonObject screenStaysOpen(Client c, String contains, int ticks) {
        screenOpen(c, contains);
        c.waitTicks(ticks);
        return screenOpen(c, contains);
    }

    /** The block at the position has this id (e.g. {@code minecraft:chest}). Reads the server side. */
    public static JsonObject block(Client c, int x, int y, int z, String id) {
        JsonObject b = c.block(x, y, z);
        equal(id, b.get("block").getAsString(), "block at " + x + "," + y + "," + z);
        return b;
    }

    /** Slot {@code index} of the open container holds {@code itemId} with {@code count} (count < 0 = any). */
    public static JsonObject slotHolds(Client c, int index, String itemId, int count) {
        JsonObject s = c.slot(index);
        that(s != null, "slot " + index + " exists on the open screen");
        that(!s.get("empty").getAsBoolean(), "slot " + index + " holds " + itemId + " but is empty");
        equal(itemId, s.get("id").getAsString(), "item in slot " + index);
        if (count >= 0) equal(count, s.get("count").getAsInt(), "count in slot " + index);
        return s;
    }

    public static void slotEmpty(Client c, int index) {
        JsonObject s = c.slot(index);
        that(s != null, "slot " + index + " exists on the open screen");
        that(s.get("empty").getAsBoolean(), "slot " + index + " empty but holds " + s.get("id"));
    }

    /** The player's inventory holds at least {@code count} of the item anywhere. */
    public static void inventoryHolds(Client c, String itemId, int count) {
        int total = 0;
        for (var e : c.inventory().getAsJsonArray("slots")) {
            JsonObject s = e.getAsJsonObject();
            if (s.get("id").getAsString().equals(itemId)) total += s.get("count").getAsInt();
        }
        that(total >= count, "inventory holds " + count + " x " + itemId + " (has " + total + ")");
    }

    /** The recent log has a line matching the regex. */
    public static String logMatches(Client c, String regex, int lines) {
        List<String> log = c.log(lines, regex);
        that(!log.isEmpty(), "log line matching /" + regex + "/ in the last " + lines + " lines");
        return log.get(log.size() - 1);
    }

    /** No recent log line matches the regex (use for "Exception", "Failed to load texture"). */
    public static void logClean(Client c, String regex, int lines) {
        List<String> log = c.log(lines, regex);
        that(log.isEmpty(), "no log line matching /" + regex + "/, but found: " + (log.isEmpty() ? "" : log.get(0)));
    }

    /** No line logged since {@link Client#markLog} matches the regex. */
    public static void logCleanSinceMark(Client c, String regex) {
        List<String> log = new ArrayList<>(c.logSinceMark(regex));
        log.removeIf(line -> line.contains("Realms"));   // offline dev clients log Realms auth failures
        that(log.isEmpty(), "no log line matching /" + regex + "/ since the mark, but found: " + (log.isEmpty() ? "" : log.get(0)));
    }

    public static void matches(String regex, String actual, String what) {
        that(actual != null && Pattern.compile(regex).matcher(actual).find(), what + " matches /" + regex + "/ but was <" + actual + ">");
    }
}
