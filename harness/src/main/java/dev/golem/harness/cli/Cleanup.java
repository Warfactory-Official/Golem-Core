package dev.golem.harness.cli;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Retention for {@code out}: screenshots pile up quickly (a run folder is tens of megabytes), so
 * {@code up}, {@code restart} and {@code run} delete what is older than {@code retentionHours}
 * ({@code harness.json}, default 10) before they start; {@code harness clean} does it on demand.
 *
 * <p>Only what the harness itself writes is touched: run folders under {@code runs/} (aged by the
 * {@code yyyyMMdd-HHmmss} stamp in their name), and files under {@code adhoc/}, {@code clients/} and
 * {@code gradle/} (aged by modification time). The log of a client that is still up is kept. Anything
 * else in {@code out}, such as a tool's own output folder, is left alone. Files another program holds
 * open are skipped and retried next time.
 */
public final class Cleanup {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private Cleanup() {}

    /** Deletes harness output older than {@code hours}; prints one line when anything was removed. */
    public static void run(double hours, State state) {
        if (hours < 0) return;
        long cutoff = System.currentTimeMillis() - (long) (hours * 3_600_000L);
        Path out = Paths.out();
        Set<Path> keep = state.all().values().stream().map(l -> Path.of(l.log()).toAbsolutePath().normalize()).collect(Collectors.toSet());
        long[] totals = new long[3]; // runs, files, bytes
        Path runs = out.resolve("runs");
        if (Files.isDirectory(runs)) {
            for (Path dir : list(runs)) {
                if (!Files.isDirectory(dir) || started(dir) >= cutoff) continue;
                long[] removed = deleteTree(dir, Set.of());
                if (!Files.exists(dir)) totals[0]++;
                totals[2] += removed[1];
            }
        }
        for (String folder : List.of("adhoc", "clients", "gradle")) {
            Path root = out.resolve(folder);
            if (!Files.isDirectory(root)) continue;
            long[] removed = deleteOlder(root, cutoff, keep);
            totals[1] += removed[0];
            totals[2] += removed[1];
        }
        if (totals[0] + totals[1] > 0) {
            System.out.printf(java.util.Locale.ROOT, "[harness] cleanup: removed %d run folder(s) and %d other file(s) older than %s h, freed %.1f MB%n",
                    totals[0], totals[1], hours == Math.rint(hours) ? Long.toString((long) hours) : Double.toString(hours), totals[2] / 1048576.0);
        }
    }

    /** Run start from the folder name, else the folder's modification time. */
    private static long started(Path dir) {
        String name = dir.getFileName().toString();
        if (name.length() >= 15) {
            try {
                return LocalDateTime.parse(name.substring(0, 15), STAMP).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // not a harness stamp
            }
        }
        try {
            return Files.getLastModifiedTime(dir).toMillis();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    private static List<Path> list(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Deletes every file (and then every emptied folder) under {@code root}; returns {files, bytes}. */
    private static long[] deleteTree(Path root, Set<Path> keep) {
        return delete(root, Long.MAX_VALUE, keep, true);
    }

    private static long[] deleteOlder(Path root, long cutoff, Set<Path> keep) {
        return delete(root, cutoff, keep, false);
    }

    private static long[] delete(Path root, long cutoff, Set<Path> keep, boolean includeRoot) {
        long[] counts = new long[2];
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.lastModifiedTime().toMillis() >= cutoff || keep.contains(file.toAbsolutePath().normalize())) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        Files.delete(file);
                        counts[0]++;
                        counts[1] += attrs.size();
                    } catch (IOException ignored) {
                        // open elsewhere; next time
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) {
                    // Folders go only with a whole run; adhoc/<node> etc. stay for the next screenshot.
                    if (!includeRoot) return FileVisitResult.CONTINUE;
                    try {
                        Files.deleteIfExists(dir); // only succeeds once empty
                    } catch (IOException ignored) {
                        // not empty or in use
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
        return counts;
    }
}
