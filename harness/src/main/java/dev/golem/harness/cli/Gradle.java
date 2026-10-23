package dev.golem.harness.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Serialised Gradle invocations with no daemon left behind. */
public final class Gradle {

    private static final Object LOCK = new Object();

    private Gradle() {}

    /** Runs {@code gradlew <args>} ({@code gradlew.bat} on Windows) in {@code project}, appending output to {@code log}. */
    public static int run(Path project, Path log, String... args) {
        synchronized (LOCK) {
            List<String> command = new ArrayList<>();
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                command.add(project.resolve("gradlew.bat").toString());
            } else {
                command.add("sh");
                command.add(project.resolve("gradlew").toString());
            }
            command.addAll(List.of(args));
            command.add("--no-daemon");
            command.add("--offline");
            command.add("--console=plain");
            // Configure only the requested node: configuring a Fabric node re-remaps the Golem jar that a
            // running Fabric client holds open, which fails the whole build.
            command.add("--configure-on-demand");
            try {
                Files.createDirectories(log.getParent());
                ProcessBuilder builder = new ProcessBuilder(command).directory(project.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
                // Gradle's own JVM must not inherit a harness port or a client heap setting.
                builder.environment().remove("GOLEM_HARNESS_PORT");
                Process process = builder.start();
                return process.waitFor();
            } catch (IOException e) {
                throw new IllegalStateException("cannot start gradle: " + e, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while running gradle");
            }
        }
    }

    public static String tail(Path log, int lines) {
        try {
            List<String> all = Files.readAllLines(log);
            int from = Math.max(0, all.size() - lines);
            return String.join("\n", all.subList(from, all.size()));
        } catch (IOException e) {
            return "(no log at " + log + ")";
        }
    }
}
