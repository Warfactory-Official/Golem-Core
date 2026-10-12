package dev.golem.core.harness;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.golem.core.GolemCore;
import java.util.ArrayDeque;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Ring buffer of recent log lines at INFO or above, filled by a Log4j appender on the root logger. Chat also
 * lands here because vanilla logs every chat line as {@code [CHAT] ...} at INFO.
 */
final class HarnessLog {

    private static final int CAPACITY = 4000;
    private static final ArrayDeque<String> lines = new ArrayDeque<>(CAPACITY);
    private static long dropped;

    private HarnessLog() {}

    static void install() {
        try {
            if (!(LogManager.getRootLogger() instanceof Logger root)) {
                GolemCore.LOG.warn("[harness] root logger is not log4j-core; log queries are unavailable");
                return;
            }
            PatternLayout layout = PatternLayout.newBuilder()
                    .withPattern("%d{HH:mm:ss.SSS} [%t/%level] [%logger{1}]: %msg")
                    .build();
            //? if >=1.21 {
            AbstractAppender appender = new AbstractAppender("GolemHarness", null, layout, true, new Property[0]) {
            //?} else {
            /*// 1.20.1 ships a Log4j without the Property[] constructor.
            AbstractAppender appender = new AbstractAppender("GolemHarness", null, layout, true) {
            *///?}
                @Override
                public void append(LogEvent event) {
                    // INFO and above only. Fabric's dev launch runs the root logger at DEBUG, where benign loader
                    // chatter lands (fabric-command-api logs every server command's failed client-side parse as
                    // "Syntax exception ... CommandSyntaxException" before forwarding it), which tripped every
                    // scenario's log check on Fabric nodes and pushed real lines out of the ring buffer.
                    if (!event.getLevel().isMoreSpecificThan(Level.INFO)) return;
                    add(layout.toSerializable(event));
                }
            };
            appender.start();
            root.addAppender(appender);
        } catch (Throwable t) {
            GolemCore.LOG.warn("[harness] could not attach the log appender: {}", t.toString());
        }
    }

    static void add(String line) {
        synchronized (lines) {
            if (lines.size() >= CAPACITY) {
                lines.removeFirst();
                dropped++;
            }
            lines.addLast(line);
        }
    }

    /**
     * Of the last {@code count} lines, those matching {@code regex} (all of them when empty). With {@code since >= 0}
     * (a {@code total} an earlier answer gave, i.e. a client's log mark) only lines logged at or after that running index
     * are considered, still at most the last {@code count}. The lines and {@code total} are read under one lock, so a
     * client's "since the mark" read cannot lose its first lines to lines logged between two queries (it did: a
     * scenario that marked, right-clicked and then read lost the right-click's sound line whenever a geiger click was
     * logged between its "how many lines now" and "give me the last N lines" queries).
     */
    static JsonObject tail(int count, String regex, long since) {
        Pattern pattern = regex == null || regex.isEmpty() ? null : Pattern.compile(regex);
        JsonArray out = new JsonArray();
        long total;
        synchronized (lines) {
            total = dropped + lines.size();
            long available = since < 0 ? count : Math.min(count, Math.max(0, total - since));
            java.util.Iterator<String> it = lines.descendingIterator();
            java.util.ArrayDeque<String> picked = new java.util.ArrayDeque<>();
            int seen = 0;
            while (it.hasNext() && seen++ < available) {
                String line = it.next();
                if (pattern == null || pattern.matcher(line).find()) picked.addFirst(line);
            }
            picked.forEach(out::add);
        }
        JsonObject json = Json.ok();
        json.add("lines", out);
        json.addProperty("dropped", dropped);
        // Running count of every line ever buffered; a client stores it as a mark and later asks only for lines after
        // the mark ("since").
        json.addProperty("total", total);
        if (since >= 0) json.addProperty("since", since);
        return json;
    }
}
