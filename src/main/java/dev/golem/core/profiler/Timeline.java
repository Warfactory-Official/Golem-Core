package dev.golem.core.profiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One main thread's samples in time order, each pointing at a node of a shared prefix tree of stacks, so the
 * report can rebuild the call tree for any time range. Node 0 is the root; a node is (parent, frame).
 */
final class Timeline {

    private final Map<String, Integer> frameIds = new HashMap<>();
    private final List<String> frames = new ArrayList<>();
    private final Map<Long, Integer> children = new HashMap<>();
    private final List<String> states = new ArrayList<>();
    private int[] parents = new int[4096];
    private int[] nodeFrames = new int[4096];
    private int nodes = 1;
    // Sample times in tenths of a millisecond since the capture began.
    private int[] times = new int[8192];
    private int[] sampleNodes = new int[8192];
    private byte[] sampleStates = new byte[8192];
    private int samples;

    void add(double seconds, StackTraceElement[] stack, String state) {
        int node = 0;
        for (int i = stack.length - 1; i >= 0; i--) {
            String key = stack[i].getClassName() + "." + stack[i].getMethodName();
            int frame = frameIds.computeIfAbsent(key, k -> {
                frames.add(k);
                return frames.size() - 1;
            });
            node = child(node, frame);
        }
        if (samples == times.length) {
            times = Arrays.copyOf(times, samples * 2);
            sampleNodes = Arrays.copyOf(sampleNodes, samples * 2);
            sampleStates = Arrays.copyOf(sampleStates, samples * 2);
        }
        int stateId = states.indexOf(state);
        if (stateId < 0) {
            states.add(state);
            stateId = states.size() - 1;
        }
        times[samples] = (int) Math.round(seconds * 10_000);
        sampleNodes[samples] = node;
        sampleStates[samples] = (byte) stateId;
        samples++;
    }

    private int child(int parent, int frame) {
        long key = ((long) parent << 32) | frame;
        Integer existing = children.get(key);
        if (existing != null) return existing;
        if (nodes == parents.length) {
            parents = Arrays.copyOf(parents, nodes * 2);
            nodeFrames = Arrays.copyOf(nodeFrames, nodes * 2);
        }
        parents[nodes] = parent;
        nodeFrames[nodes] = frame;
        children.put(key, nodes);
        return nodes++;
    }

    int size() {
        return samples;
    }

    double seconds(int sample) {
        return times[sample] / 10_000.0;
    }

    String state(int sample) {
        return states.get(sampleStates[sample]);
    }

    /** The sample's stack, innermost frame first, as a thread dump gives it. */
    StackTraceElement[] stack(int sample) {
        int depth = 0;
        for (int node = sampleNodes[sample]; node != 0; node = parents[node]) depth++;
        StackTraceElement[] stack = new StackTraceElement[depth];
        int i = 0;
        for (int node = sampleNodes[sample]; node != 0; node = parents[node]) {
            String frame = frames.get(nodeFrames[node]);
            int dot = frame.lastIndexOf('.');
            stack[i++] = new StackTraceElement(frame.substring(0, dot), frame.substring(dot + 1), null, -1);
        }
        return stack;
    }

    /** {@code frames, parents, nodeFrames} describe the tree; {@code t, n, s} are each sample's time, node and state. */
    JsonObject toJson(String thread, int intervalMs) {
        JsonObject json = new JsonObject();
        json.addProperty("thread", thread);
        json.addProperty("intervalMs", intervalMs);
        JsonArray frameArray = new JsonArray();
        frames.forEach(frameArray::add);
        json.add("frames", frameArray);
        json.add("parents", ints(parents, nodes));
        json.add("nodeFrames", ints(nodeFrames, nodes));
        json.add("t", ints(times, samples));
        json.add("n", ints(sampleNodes, samples));
        JsonArray stateIds = new JsonArray();
        for (int i = 0; i < samples; i++) stateIds.add(sampleStates[i]);
        json.add("s", stateIds);
        JsonArray stateNames = new JsonArray();
        states.forEach(stateNames::add);
        json.add("states", stateNames);
        return json;
    }

    private static JsonArray ints(int[] values, int count) {
        JsonArray array = new JsonArray();
        for (int i = 0; i < count; i++) array.add(values[i]);
        return array;
    }
}
