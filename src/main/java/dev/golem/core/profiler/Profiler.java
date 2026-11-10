package dev.golem.core.profiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.golem.core.GolemCore;
import dev.golem.core.platform.ConfigFiles;
import dev.golem.core.platform.Platform;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;

/**
 * One profiling capture at a time for the whole JVM: CPU samples of every thread, the flight recorder, heap and GC,
 * plus what each {@link Probe} measures (client frames and GPU time, server ticks, world census). Writes a
 * self-contained HTML report and the raw {@code .jfr} to {@code <game dir>/golem}.
 */
public final class Profiler {

    public static final int DEFAULT_SECONDS = 30;
    public static final int MAX_SECONDS = 600;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static @Nullable Capture current;
    private static volatile long origin = System.nanoTime();

    private Profiler() {}

    /** Something measured alongside the samples. {@link #collect} may hop to its own thread; it is given 10 s. */
    public interface Probe {
        String key();

        void start();

        void stop();

        CompletableFuture<JsonElement> collect();

        /** The slow frames or ticks this probe saw, called after {@link #collect}. */
        default @Nullable Spikes spikes() {
            return null;
        }
    }

    /** {@code [from, to]} windows in capture seconds during which {@code thread} ran over {@code thresholdMs}. */
    public record Spikes(String thread, String label, double thresholdMs, List<double[]> windows) {}

    /** Called on the capture thread; implementations hop to their own thread to talk to players. */
    public interface Listener {
        void finished(Path report);

        void failed(String reason);
    }

    /** Seconds since the running capture began; every timeline in a report shares this clock. */
    static double seconds(long nanos) {
        return (nanos - origin) / 1e9;
    }

    public static synchronized boolean isRunning() {
        return current != null;
    }

    /** Seconds left in the running capture, or 0. */
    public static synchronized int secondsLeft() {
        if (current == null) return 0;
        return (int) Math.max(0, Math.ceil((current.deadline - System.nanoTime()) / 1e9));
    }

    /** Starts a capture; false when one is already running. */
    public static synchronized boolean start(int seconds, String side, List<Probe> probes, Listener listener) {
        if (current != null) return false;
        Capture capture = new Capture(Math.max(1, Math.min(MAX_SECONDS, seconds)), side, probes, listener);
        current = capture;
        capture.begin();
        return true;
    }

    /** Ends the running capture early; it still writes its report. */
    public static synchronized boolean stop() {
        if (current == null) return false;
        current.done.countDown();
        return true;
    }

    private static synchronized void finished(Capture capture) {
        if (current == capture) current = null;
    }

    public static Path directory() {
        return ConfigFiles.configDir().toAbsolutePath().getParent().resolve("golem");
    }

    private static final class Capture implements Runnable {
        final int seconds;
        final String side;
        final List<Probe> probes;
        final Listener listener;
        final CountDownLatch done = new CountDownLatch(1);
        final StackSampler sampler = new StackSampler();
        final Thread samplerThread = new Thread(sampler, "Golem profiler sampler");
        final JsonArray memory = new JsonArray();
        final String stamp = LocalDateTime.now().format(STAMP);
        long deadline;
        long startNanos;
        @Nullable JfrRecorder jfr;
        @Nullable String jfrError;

        Capture(int seconds, String side, List<Probe> probes, Listener listener) {
            this.seconds = seconds;
            this.side = side;
            this.probes = probes;
            this.listener = listener;
        }

        void begin() {
            startNanos = System.nanoTime();
            origin = startNanos;
            deadline = startNanos + seconds * 1_000_000_000L;
            try {
                jfr = JfrRecorder.start();
            } catch (Exception | LinkageError unavailable) {
                // No jdk.jfr module, or recording refused (another tool holds it): the report omits JFR sections.
                jfrError = String.valueOf(unavailable);
            }
            for (Probe probe : probes) probe.start();
            samplerThread.setDaemon(true);
            samplerThread.setPriority(Thread.MAX_PRIORITY);
            samplerThread.start();
            Thread thread = new Thread(() -> {
                try {
                    run();
                } finally {
                    sampler.stop();
                    finished(this);
                }
            }, "Golem profiler");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void run() {
            try {
                while (!done.await(1, TimeUnit.SECONDS)) {
                    recordMemory();
                    if (System.nanoTime() >= deadline) break;
                }
                double elapsed = (System.nanoTime() - startNanos) / 1e9;
                sampler.stop();
                for (Probe probe : probes) probe.stop();
                listener.finished(write(elapsed));
            } catch (Exception | LinkageError failure) {
                GolemCore.LOG.error("Profiler capture failed", failure);
                for (Probe probe : probes) probe.stop();
                listener.failed(String.valueOf(failure));
            }
        }

        private void recordMemory() {
            MemoryMXBean bean = ManagementFactory.getMemoryMXBean();
            long gcCount = 0;
            long gcMillis = 0;
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                gcCount += Math.max(0, gc.getCollectionCount());
                gcMillis += Math.max(0, gc.getCollectionTime());
            }
            JsonArray row = new JsonArray();
            row.add((System.nanoTime() - startNanos) / 1e9);
            row.add(bean.getHeapMemoryUsage().getUsed() / 1048576.0);
            row.add(bean.getHeapMemoryUsage().getCommitted() / 1048576.0);
            row.add(bean.getNonHeapMemoryUsage().getUsed() / 1048576.0);
            row.add(gcCount);
            row.add(gcMillis);
            memory.add(row);
        }

        private Path write(double elapsed) throws IOException, InterruptedException {
            Path dir = directory();
            Files.createDirectories(dir);
            String base = "profile-" + stamp + "-" + side;
            JsonObject report = new JsonObject();
            JsonObject meta = new JsonObject();
            meta.addProperty("created", stamp);
            meta.addProperty("side", side);
            meta.addProperty("requestedSeconds", seconds);
            meta.addProperty("seconds", elapsed);
            meta.addProperty("minecraft", GolemCore.MC_VERSION);
            meta.addProperty("loader", Platform.name());
            meta.addProperty("golemCore", Platform.modVersion(GolemCore.MOD_ID));
            report.add("meta", meta);
            report.add("system", SystemInfo.collect());
            report.add("sampler", waitForSampler());
            report.add("memory", memory);
            if (jfr != null) {
                try {
                    report.add("jfr", jfr.finish(dir.resolve(base + ".jfr")));
                    meta.addProperty("jfrFile", base + ".jfr");
                } catch (IOException | RuntimeException failed) {
                    // A dump or parse failure costs the JFR sections only, not the capture.
                    jfrError = String.valueOf(failed);
                }
            }
            if (jfrError != null) meta.addProperty("jfrError", jfrError);
            JsonObject probeData = new JsonObject();
            for (Probe probe : probes) {
                try {
                    probeData.add(probe.key(), probe.collect().get(10, TimeUnit.SECONDS));
                } catch (TimeoutException | ExecutionException failed) {
                    JsonObject error = new JsonObject();
                    error.addProperty("error", String.valueOf(failed));
                    probeData.add(probe.key(), error);
                }
            }
            report.add("probes", probeData);
            JsonArray spikes = new JsonArray();
            for (Probe probe : probes) {
                Spikes found = probe.spikes();
                if (found == null) continue;
                JsonObject entry = new JsonObject();
                entry.addProperty("thread", found.thread());
                entry.addProperty("label", found.label());
                entry.addProperty("thresholdMs", found.thresholdMs());
                entry.addProperty("count", found.windows().size());
                JsonObject profile = sampler.spikeTree(found.thread(), found.windows());
                if (profile != null) entry.add("profile", profile);
                spikes.add(entry);
            }
            report.add("spikes", spikes);
            Path html = dir.resolve(base + ".html");
            ReportWriter.write(report, html);
            return html;
        }

        private JsonObject waitForSampler() throws InterruptedException {
            samplerThread.join(5000);
            return sampler.toJson();
        }
    }
}
