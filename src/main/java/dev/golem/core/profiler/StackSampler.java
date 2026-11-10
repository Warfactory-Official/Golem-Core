package dev.golem.core.profiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import org.jspecify.annotations.Nullable;

/**
 * A wall-clock sampling profiler over thread dumps. The game's main threads are sampled every
 * {@value #MAIN_INTERVAL_MS} ms; every other thread every {@value #POOL_INTERVAL_MS} ms, folded into pools by name.
 * Needs no agent and works on every JVM; dumps stop at safepoints, so very short methods are under-counted.
 */
final class StackSampler implements Runnable {

    static final int MAIN_INTERVAL_MS = 4;
    static final int POOL_INTERVAL_MS = 20;
    private static final int MAX_DEPTH = 384;
    private static final String[] MAIN_THREADS = {"Render thread", "Server thread"};

    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private final Map<String, ThreadProfile> profiles = new HashMap<>();
    private final Map<Long, long[]> cpuStart = new HashMap<>();
    private final Map<Long, String> names = new HashMap<>();
    private final Map<String, Timeline> timelines = new HashMap<>();
    private final Map<Long, Long> lastCpu = new HashMap<>();
    private volatile boolean running = true;
    private long mainSamples;
    private long poolSamples;
    private long dumpNanos;
    private long elapsedNanos;

    void stop() {
        running = false;
    }

    @Override
    public void run() {
        long self = Thread.currentThread().getId();
        long[] main = mainThreadIds();
        recordCpuStart();
        long start = System.nanoTime();
        long next = start;
        long nextPool = start;
        while (running) {
            long before = System.nanoTime();
            if (before >= nextPool) {
                sampleAll(self, main);
                nextPool += POOL_INTERVAL_MS * 1_000_000L;
                if (nextPool < before) nextPool = before + POOL_INTERVAL_MS * 1_000_000L;
            } else if (main.length > 0) {
                sample(threads.getThreadInfo(main, MAX_DEPTH), MAIN_INTERVAL_MS, true);
                mainSamples++;
            }
            dumpNanos += System.nanoTime() - before;
            next += MAIN_INTERVAL_MS * 1_000_000L;
            long wait = next - System.nanoTime();
            if (wait > 0) LockSupport.parkNanos(wait);
            else next = System.nanoTime();
        }
        elapsedNanos = System.nanoTime() - start;
    }

    private void sampleAll(long self, long[] main) {
        ThreadInfo[] all = threads.dumpAllThreads(false, false, MAX_DEPTH);
        for (ThreadInfo info : all) {
            if (info == null || info.getThreadId() == self) continue;
            boolean isMain = contains(main, info.getThreadId());
            add(info, isMain ? MAIN_INTERVAL_MS : POOL_INTERVAL_MS, isMain);
        }
        poolSamples++;
        mainSamples++;
    }

    private void sample(ThreadInfo[] infos, int intervalMs, boolean main) {
        for (ThreadInfo info : infos) if (info != null) add(info, intervalMs, main);
    }

    private void add(ThreadInfo info, int intervalMs, boolean main) {
        Thread.State state = info.getThreadState();
        // Idle pool threads would bury the busy ones; the main threads keep every state, since a waiting
        // render thread is a finding (vsync, GPU sync, a lock).
        boolean busy = main || usedCpu(info.getThreadId());
        if (!main && (state != Thread.State.RUNNABLE || !busy)) {
            countIdle(info);
            return;
        }
        String name = main ? info.getThreadName() : poolName(info.getThreadName());
        names.putIfAbsent(info.getThreadId(), info.getThreadName());
        StackTraceElement[] stack = info.getStackTrace();
        profiles.computeIfAbsent(name, ThreadProfile::new).add(stack, state.name(), intervalMs * 1000L);
        if (main) {
            timelines.computeIfAbsent(name, k -> new Timeline()).add(Profiler.seconds(System.nanoTime()), stack, state.name());
        }
    }

    private void countIdle(ThreadInfo info) {
        names.putIfAbsent(info.getThreadId(), info.getThreadName());
        String state = info.getThreadState() == Thread.State.RUNNABLE ? "IDLE_NATIVE" : info.getThreadState().name();
        profiles.computeIfAbsent(poolName(info.getThreadName()), ThreadProfile::new)
                .states.merge(state, POOL_INTERVAL_MS * 1000L, Long::sum);
    }

    /**
     * Whether a pool thread used at least 5% of a core since its last dump. Threads blocked in native code (socket
     * polls, signal and reference handlers) report RUNNABLE while idle.
     */
    private boolean usedCpu(long id) {
        if (!threads.isThreadCpuTimeSupported()) return true;
        long now = threads.getThreadCpuTime(id);
        Long before = lastCpu.put(id, now);
        return before == null || now < 0 || now - before >= POOL_INTERVAL_MS * 50_000L;
    }

    private long[] mainThreadIds() {
        List<Long> ids = new ArrayList<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            for (String name : MAIN_THREADS) if (name.equals(thread.getName())) ids.add(thread.getId());
        }
        return ids.stream().mapToLong(Long::longValue).toArray();
    }

    private void recordCpuStart() {
        if (!threads.isThreadCpuTimeSupported()) return;
        for (long id : threads.getAllThreadIds()) {
            long cpu = threads.getThreadCpuTime(id);
            cpuStart.put(id, new long[]{cpu});
            lastCpu.put(id, cpu);
        }
    }

    /** Folds pool numbering away: "Worker-Main-12" and "Worker-Main-3" are one pool. */
    static String poolName(String name) {
        String folded = name.replaceAll("\\d+", "#");
        return folded.equals(name) ? name : folded;
    }

    private static boolean contains(long[] ids, long id) {
        for (long candidate : ids) if (candidate == id) return true;
        return false;
    }

    /**
     * A call tree of {@code thread}'s samples that fall inside the given {@code [from, to]} windows (seconds, sorted
     * by start): what the thread did during slow frames or ticks only.
     */
    @Nullable JsonObject spikeTree(String thread, List<double[]> windows) {
        Timeline timeline = timelines.get(thread);
        if (timeline == null || windows.isEmpty()) return null;
        ThreadProfile profile = new ThreadProfile(thread);
        int w = 0;
        for (int i = 0; i < timeline.size(); i++) {
            double time = timeline.seconds(i);
            while (w < windows.size() && windows.get(w)[1] < time) w++;
            if (w == windows.size()) break;
            if (time >= windows.get(w)[0]) profile.add(timeline.stack(i), timeline.state(i), MAIN_INTERVAL_MS * 1000L);
        }
        return profile.samples == 0 ? null : profile.toJson(200);
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("mainIntervalMs", MAIN_INTERVAL_MS);
        json.addProperty("poolIntervalMs", POOL_INTERVAL_MS);
        json.addProperty("mainSamples", mainSamples);
        json.addProperty("poolSamples", poolSamples);
        json.addProperty("elapsedMs", elapsedNanos / 1e6);
        json.addProperty("overheadMs", dumpNanos / 1e6);
        List<ThreadProfile> sorted = new ArrayList<>(profiles.values());
        sorted.sort((a, b) -> isMain(a) != isMain(b) ? (isMain(a) ? -1 : 1) : Long.compare(b.weight, a.weight));
        JsonArray array = new JsonArray();
        for (ThreadProfile profile : sorted) {
            if (profile.samples == 0) continue;
            array.add(profile.toJson(isMain(profile) ? 400 : 150));
        }
        json.add("threads", array);
        JsonArray timelineArray = new JsonArray();
        timelines.forEach((thread, timeline) -> timelineArray.add(timeline.toJson(thread, MAIN_INTERVAL_MS)));
        json.add("timelines", timelineArray);
        json.add("idle", idleJson());
        json.add("cpu", cpuJson());
        return json;
    }

    private static boolean isMain(ThreadProfile profile) {
        for (String name : MAIN_THREADS) if (name.equals(profile.name)) return true;
        return false;
    }

    private JsonArray idleJson() {
        JsonArray idle = new JsonArray();
        for (ThreadProfile profile : profiles.values()) {
            if (profile.samples > 0) continue;
            JsonArray row = new JsonArray();
            row.add(profile.name);
            JsonObject states = new JsonObject();
            profile.states.forEach((state, micros) -> states.addProperty(state, micros / 1000.0));
            row.add(states);
            idle.add(row);
        }
        return idle;
    }

    /** CPU time each thread used during the capture, which unlike samples counts native code exactly. */
    private JsonArray cpuJson() {
        JsonArray rows = new JsonArray();
        if (!threads.isThreadCpuTimeSupported()) return rows;
        for (Map.Entry<Long, long[]> entry : cpuStart.entrySet()) {
            long now = threads.getThreadCpuTime(entry.getKey());
            long start = entry.getValue()[0];
            if (now < 0 || start < 0 || now - start < 1_000_000L) continue;
            JsonArray row = new JsonArray();
            String name = names.get(entry.getKey());
            if (name == null) {
                ThreadInfo info = threads.getThreadInfo(entry.getKey());
                name = info == null ? "thread " + entry.getKey() : info.getThreadName();
            }
            row.add(name);
            row.add((now - start) / 1e6);
            rows.add(row);
        }
        return rows;
    }
}
