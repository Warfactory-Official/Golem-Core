package dev.golem.core.profiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stack samples of one thread (or one pool of same-named threads) folded into a call tree, per-method self and
 * total counts and per-code-group counts. A sample's weight is the sampling interval in microseconds, so pools
 * sampled less often still add up to wall-clock time.
 */
final class ThreadProfile {

    final String name;
    long weight;
    long samples;
    final Map<String, Long> states = new HashMap<>();
    final Node root = new Node("all");
    private final Map<String, long[]> methods = new HashMap<>();
    private final Map<String, long[]> classes = new HashMap<>();
    private final Map<String, long[]> groups = new HashMap<>();
    private final Set<String> seen = new HashSet<>();
    private final Set<String> seenClasses = new HashSet<>();
    private final Set<String> seenGroups = new HashSet<>();

    ThreadProfile(String name) {
        this.name = name;
    }

    /** Adds one sample; {@code frames} run from the innermost call outwards, as {@link StackTraceElement}s do. */
    void add(StackTraceElement[] frames, String state, long weightMicros) {
        samples++;
        weight += weightMicros;
        states.merge(state, weightMicros, Long::sum);
        Node node = root;
        node.total += weightMicros;
        seen.clear();
        seenClasses.clear();
        seenGroups.clear();
        if (frames.length == 0) {
            node = node.child("[" + state.toLowerCase() + ", no Java frames]");
            node.total += weightMicros;
            node.self += weightMicros;
            return;
        }
        for (int i = frames.length - 1; i >= 0; i--) {
            StackTraceElement frame = frames[i];
            String key = frame.getClassName() + "." + frame.getMethodName();
            node = node.child(key);
            node.total += weightMicros;
            if (seen.add(key)) methods.computeIfAbsent(key, k -> new long[2])[1] += weightMicros;
            if (seenClasses.add(frame.getClassName())) {
                classes.computeIfAbsent(frame.getClassName(), k -> new long[2])[1] += weightMicros;
            }
            String group = CodeGroups.of(frame.getClassName(), frame.getMethodName());
            if (seenGroups.add(group)) groups.computeIfAbsent(group, k -> new long[2])[1] += weightMicros;
        }
        node.self += weightMicros;
        StackTraceElement top = frames[0];
        methods.get(top.getClassName() + "." + top.getMethodName())[0] += weightMicros;
        classes.get(top.getClassName())[0] += weightMicros;
        groups.get(CodeGroups.of(top.getClassName(), top.getMethodName()))[0] += weightMicros;
    }

    JsonObject toJson(int maxMethods) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("samples", samples);
        json.addProperty("timeMs", weight / 1000.0);
        JsonObject stateJson = new JsonObject();
        states.forEach((state, micros) -> stateJson.addProperty(state, micros / 1000.0));
        json.add("states", stateJson);
        json.add("methods", table(methods, maxMethods));
        json.add("classes", table(classes, maxMethods));
        json.add("groups", table(groups, Integer.MAX_VALUE));
        // Nodes under 1/4000 of the thread's time are folded away; they are noise in a flame graph.
        json.add("tree", root.toJson(Math.max(1, weight / 4000)));
        return json;
    }

    /** {@code [name, selfMs, totalMs]} rows: the top {@code limit} by self time and the top {@code limit} by total. */
    private static JsonArray table(Map<String, long[]> entries, int limit) {
        List<Map.Entry<String, long[]>> sorted = new ArrayList<>(entries.entrySet());
        Set<String> kept = new HashSet<>();
        sorted.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        for (int i = 0; i < sorted.size() && i < limit; i++) kept.add(sorted.get(i).getKey());
        sorted.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
        for (int i = 0; i < sorted.size() && i < limit; i++) kept.add(sorted.get(i).getKey());
        JsonArray array = new JsonArray();
        for (Map.Entry<String, long[]> entry : sorted) {
            if (!kept.contains(entry.getKey())) continue;
            JsonArray row = new JsonArray();
            row.add(entry.getKey());
            row.add(entry.getValue()[0] / 1000.0);
            row.add(entry.getValue()[1] / 1000.0);
            array.add(row);
        }
        return array;
    }

    static final class Node {
        final String frame;
        long total;
        long self;
        private Map<String, Node> children;

        Node(String frame) {
            this.frame = frame;
        }

        Node child(String key) {
            if (children == null) children = new HashMap<>();
            return children.computeIfAbsent(key, Node::new);
        }

        Node find(String key) {
            return children == null ? null : children.get(key);
        }

        /** {@code [frame, totalMs, selfMs, children?]}: compact, as a busy thread has tens of thousands of nodes. */
        JsonArray toJson(long minimum) {
            JsonArray json = new JsonArray();
            json.add(frame);
            json.add(total / 1000.0);
            json.add(self / 1000.0);
            if (children != null) {
                JsonArray kids = new JsonArray();
                List<Node> sorted = new ArrayList<>(children.values());
                sorted.sort((a, b) -> Long.compare(b.total, a.total));
                for (Node child : sorted) if (child.total >= minimum) kids.add(child.toJson(minimum));
                if (!kids.isEmpty()) json.add(kids);
            }
            return json;
        }
    }
}
