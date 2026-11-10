package dev.golem.core.profiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/**
 * The JVM's own flight recorder: GC pauses, heap, allocation sites, lock contention, CPU load and safepoint-free
 * execution samples. Any JVM the game runs on ships it (vanilla's {@code /jfr} uses it); a missing module only
 * leaves those sections out.
 */
final class JfrRecorder {

    private final Recording recording;
    private final Instant start = Instant.now();

    private JfrRecorder(Recording recording) {
        this.recording = recording;
    }

    static JfrRecorder start() throws IOException, ParseException {
        Recording recording = new Recording(Configuration.getConfiguration("profile"));
        recording.setName("Golem profiler");
        recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
        recording.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(10));
        recording.enable("jdk.CPULoad").withPeriod(Duration.ofSeconds(1));
        recording.enable("jdk.ThreadCPULoad").withPeriod(Duration.ofSeconds(1));
        recording.enable("jdk.GCHeapSummary");
        recording.enable("jdk.GarbageCollection");
        recording.enable("jdk.ObjectAllocationSample").with("throttle", "500/s");
        recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1));
        recording.start();
        return new JfrRecorder(recording);
    }

    /** Stops the recording, keeps the raw file next to the report and folds it into the report's sections. */
    JsonObject finish(Path file) throws IOException {
        recording.stop();
        recording.dump(file);
        recording.close();
        Parsed parsed = new Parsed();
        try (RecordingFile events = new RecordingFile(file)) {
            while (events.hasMoreEvents()) parsed.accept(events.readEvent());
        }
        return parsed.toJson();
    }

    private final class Parsed {
        private final JsonArray cpu = new JsonArray();
        private final JsonArray heap = new JsonArray();
        private final JsonArray gcs = new JsonArray();
        private final Map<String, double[]> threadCpu = new HashMap<>();
        private final Map<String, long[]> allocations = new HashMap<>();
        private final Map<String, long[]> allocatedTypes = new HashMap<>();
        private final Map<String, double[]> locks = new HashMap<>();
        private final Map<String, long[]> execution = new HashMap<>();
        // Bottom-up: JFR keeps only the innermost 64 frames, so trees grow from the leaf towards its callers.
        private final ThreadProfile.Node cpuTree = new ThreadProfile.Node("all threads");
        private final ThreadProfile.Node allocationTree = new ThreadProfile.Node("all allocations");
        private final Map<Long, RecordedEvent> earliestAllocation = new HashMap<>();
        private long executionSamples;
        private long allocatedTotal;

        void accept(RecordedEvent event) {
            if (isProfiler(event)) return;
            switch (event.getEventType().getName()) {
                case "jdk.CPULoad" -> {
                    JsonArray row = new JsonArray();
                    row.add(seconds(event.getStartTime()));
                    row.add(event.getFloat("jvmUser") + event.getFloat("jvmSystem"));
                    row.add(event.getFloat("machineTotal"));
                    cpu.add(row);
                }
                case "jdk.ThreadCPULoad" -> {
                    RecordedThread thread = event.getThread();
                    if (thread == null) return;
                    double[] load = threadCpu.computeIfAbsent(threadName(thread), k -> new double[2]);
                    load[0] += event.getFloat("user") + event.getFloat("system");
                    load[1]++;
                }
                case "jdk.GCHeapSummary" -> {
                    JsonArray row = new JsonArray();
                    row.add(seconds(event.getStartTime()));
                    row.add(event.getLong("heapUsed") / 1048576.0);
                    row.add(event.getString("when"));
                    heap.add(row);
                }
                case "jdk.GarbageCollection" -> {
                    JsonArray row = new JsonArray();
                    row.add(seconds(event.getStartTime()));
                    row.add(event.getString("name"));
                    row.add(event.getString("cause"));
                    row.add(millis(event.getDuration("sumOfPauses")));
                    row.add(millis(event.getDuration("longestPause")));
                    row.add(millis(event.getDuration()));
                    gcs.add(row);
                }
                case "jdk.ObjectAllocationSample" -> {
                    // A thread's earliest sample weighs everything it allocated since it started, before the capture.
                    // The file is not in time order, so the earliest one per thread is held back and dropped at the end.
                    RecordedThread allocating = event.getThread();
                    if (allocating == null) return;
                    RecordedEvent earliest = earliestAllocation.get(allocating.getId());
                    if (earliest == null || event.getStartTime().isBefore(earliest.getStartTime())) {
                        earliestAllocation.put(allocating.getId(), event);
                        if (earliest != null) addAllocation(earliest);
                    } else {
                        addAllocation(event);
                    }
                }
                case "jdk.JavaMonitorEnter" -> {
                    RecordedClass monitor = event.getClass("monitorClass");
                    String key = (monitor == null ? "?" : monitor.getName()) + " at " + site(event.getStackTrace())
                            + " (" + threadName(event.getThread()) + ")";
                    double[] lock = locks.computeIfAbsent(key, k -> new double[3]);
                    double ms = millis(event.getDuration());
                    lock[0]++;
                    lock[1] += ms;
                    lock[2] = Math.max(lock[2], ms);
                }
                case "jdk.ExecutionSample", "jdk.NativeMethodSample" -> {
                    RecordedStackTrace trace = event.getStackTrace();
                    if (trace == null || trace.getFrames().isEmpty()) return;
                    executionSamples++;
                    RecordedFrame top = trace.getFrames().get(0);
                    String thread = threadName(event.getThread("sampledThread"));
                    String key = thread + "\u0000" + top.getMethod().getType().getName() + "." + top.getMethod().getName();
                    execution.computeIfAbsent(key, k -> new long[1])[0]++;
                    addInverted(cpuTree, thread, trace, 10_000);
                }
                default -> {
                }
            }
        }

        /** The profiler's own sampling and writing stay out of what it reports. */
        private boolean isProfiler(RecordedEvent event) {
            RecordedThread thread = event.hasField("sampledThread") ? event.getThread("sampledThread") : event.getThread();
            return thread != null && thread.getJavaName() != null && thread.getJavaName().startsWith("Golem profiler");
        }

        private void addAllocation(RecordedEvent event) {
            long weight = event.getLong("weight");
            allocatedTotal += weight;
            RecordedClass type = event.getClass("objectClass");
            String typeName = type == null ? "?" : type.getName();
            allocatedTypes.computeIfAbsent(typeName, k -> new long[1])[0] += weight;
            String site = typeName + " at " + site(event.getStackTrace());
            allocations.computeIfAbsent(site, k -> new long[1])[0] += weight;
            addInverted(allocationTree, typeName, event.getStackTrace(), weight);
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.add("cpu", cpu);
            json.add("heap", heap);
            json.add("gc", gcs);
            json.addProperty("allocatedMb", allocatedTotal / 1048576.0);
            json.add("allocationSites", top(allocations, 150, 1048576.0));
            json.add("allocationTypes", top(allocatedTypes, 80, 1048576.0));
            JsonArray threadRows = new JsonArray();
            threadCpu.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue()[0] / b.getValue()[1], a.getValue()[0] / a.getValue()[1]))
                    .limit(60)
                    .forEach(entry -> {
                        JsonArray row = new JsonArray();
                        row.add(entry.getKey());
                        row.add(entry.getValue()[0] / entry.getValue()[1]);
                        threadRows.add(row);
                    });
            json.add("threadCpu", threadRows);
            JsonArray lockRows = new JsonArray();
            locks.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1]))
                    .limit(80)
                    .forEach(entry -> {
                        JsonArray row = new JsonArray();
                        row.add(entry.getKey());
                        row.add(entry.getValue()[0]);
                        row.add(entry.getValue()[1]);
                        row.add(entry.getValue()[2]);
                        lockRows.add(row);
                    });
            json.add("locks", lockRows);
            JsonArray samples = new JsonArray();
            List<Map.Entry<String, long[]>> sorted = new ArrayList<>(execution.entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
            for (int i = 0; i < sorted.size() && i < 200; i++) {
                String[] parts = sorted.get(i).getKey().split("\u0000", 2);
                JsonArray row = new JsonArray();
                row.add(parts[0]);
                row.add(parts[1]);
                row.add(sorted.get(i).getValue()[0]);
                samples.add(row);
            }
            json.addProperty("executionSamples", executionSamples);
            json.add("executionTop", samples);
            json.add("cpuTree", cpuTree.toJson(Math.max(1, cpuTree.total / 4000)));
            json.add("allocationTree", allocationTree.toJson(Math.max(1, allocationTree.total / 4000)));
            return json;
        }

        private double seconds(Instant time) {
            return Duration.between(start, time).toMillis() / 1000.0;
        }
    }

    /** Adds {@code trace} below the {@code first} child of {@code root}, innermost frame first. */
    private static void addInverted(ThreadProfile.Node root, String first, RecordedStackTrace trace, long weight) {
        ThreadProfile.Node node = root.child(first);
        root.total += weight;
        node.total += weight;
        if (trace != null) {
            for (RecordedFrame frame : trace.getFrames()) {
                node = node.child(frame.getMethod().getType().getName() + "." + frame.getMethod().getName());
                node.total += weight;
            }
        }
        node.self += weight;
    }

    /** The innermost frame outside the JDK: where the game asked for the allocation or the lock. */
    private static String site(RecordedStackTrace trace) {
        if (trace == null) return "?";
        String fallback = "?";
        for (RecordedFrame frame : trace.getFrames()) {
            if (!frame.isJavaFrame()) continue;
            String type = frame.getMethod().getType().getName();
            String method = type + "." + frame.getMethod().getName() + ":" + frame.getLineNumber();
            if (fallback.equals("?")) fallback = method;
            if (!type.startsWith("java.") && !type.startsWith("jdk.") && !type.startsWith("sun.")) return method;
        }
        return fallback;
    }

    private static String threadName(RecordedThread thread) {
        if (thread == null) return "?";
        String name = thread.getJavaName() != null ? thread.getJavaName() : thread.getOSName();
        return name == null ? "?" : StackSampler.poolName(name);
    }

    private static double millis(Duration duration) {
        return duration == null ? 0 : duration.toNanos() / 1e6;
    }

    private static JsonArray top(Map<String, long[]> values, int limit, double scale) {
        JsonArray rows = new JsonArray();
        values.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(limit)
                .forEach(entry -> {
                    JsonArray row = new JsonArray();
                    row.add(entry.getKey());
                    row.add(entry.getValue()[0] / scale);
                    rows.add(row);
                });
        return rows;
    }
}
