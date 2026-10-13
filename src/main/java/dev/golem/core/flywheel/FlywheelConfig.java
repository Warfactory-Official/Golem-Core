package dev.golem.core.flywheel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.golem.core.GolemCore;
import dev.golem.core.platform.ConfigFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * The player-facing backend switch for Core's vendored engine: {@code config/golemcore-flywheel.json}.
 *
 * <p>Shaped after the published engine's own client config ({@code flywheel.json} on Fabric,
 * {@code flywheel-client.toml} on NeoForge): one {@code backend} key, {@code DEFAULT} or one of {@code off},
 * {@code instancing}, {@code indirect}, with or without the published {@code flywheel:} prefix. Turning the
 * published engine off on 1.20.1/1.21.1 and this one off on 26.1 therefore works the same way.
 * {@code /flywheel backend <value>} writes the same file.
 *
 * <p>The JVM switch {@code -Dgolem.flywheel.backend} and the environment variable {@code GOLEM_FLYWHEEL_BACKEND}
 * win over the file, for dev clients launched through Gradle.
 *
 * <p>Common code: it names no engine type, so reading it on a node without the engine is harmless, and nothing
 * below 26.1 writes it.
 */
public final class FlywheelConfig {

    public static final String FILE_NAME = "golemcore-flywheel.json";
    public static final String DEFAULT = "DEFAULT";
    /** Values the engine recognises, in the published engine's spelling order. */
    public static final List<String> VALUES = List.of(DEFAULT, "off", "instancing", "indirect");
    static final List<String> OFF = List.of("off", "none", "disabled");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private FlywheelConfig() {}

    public static Path path() {
        return ConfigFiles.configDir().resolve(FILE_NAME);
    }

    /**
     * The configured backend, normalised: lower case, {@code flywheel:} stripped, and the empty string
     * for {@code DEFAULT}, a missing file, or anything unreadable.
     */
    public static String backend() {
        Path file;
        try {
            file = path();
        } catch (RuntimeException | LinkageError unavailable) {
            // Too early for the loader's paths, or a unit test with no loader at all.
            return "";
        }
        if (!Files.isRegularFile(file)) return "";
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (json instanceof JsonObject object && object.has("backend")) {
                return normalise(object.get("backend").getAsString());
            }
        } catch (IOException | RuntimeException bad) {
            GolemCore.LOG.warn("Could not read {}; using the default backend", file, bad);
        }
        return "";
    }

    /** Writes {@code value} (any accepted spelling) as the configured backend. */
    public static void write(String value) throws IOException {
        JsonObject object = new JsonObject();
        object.addProperty("_comment", "Golem Core's instancing engine (26.1). backend: DEFAULT, off, "
                + "instancing or indirect. Off draws every machine through its vanilla renderer. "
                + "-Dgolem.flywheel.backend / GOLEM_FLYWHEEL_BACKEND override this file.");
        String normalised = normalise(value);
        object.addProperty("backend", normalised.isEmpty() ? DEFAULT : normalised);
        Path file = path();
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(object), StandardCharsets.UTF_8);
    }

    /** Writes the default file if there is none, so the switch is discoverable. Never overwrites. */
    public static void writeDefaultIfMissing() {
        try {
            if (!Files.exists(path())) write(DEFAULT);
        } catch (IOException | RuntimeException ignored) {
            // A read-only config folder only costs discoverability.
        }
    }

    /** Lower case, {@code flywheel:} prefix removed, {@code default} as the empty string. */
    public static String normalise(String value) {
        if (value == null) return "";
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("flywheel:")) lower = lower.substring("flywheel:".length());
        return lower.equals("default") ? "" : lower;
    }

    /** Whether a normalised value means no engine at all. */
    public static boolean isOff(String normalised) {
        return OFF.contains(normalised);
    }
}
