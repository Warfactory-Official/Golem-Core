package dev.golem.harness.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Live clients: {@code harness/.state/state.json}, node name to process facts. */
public final class State {

    public record Live(String node, long pid, int port, String runDir, String log, String world, long startedAt) {
        public boolean alive() {
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Live> live = new LinkedHashMap<>();

    public static State load() {
        State state = new State();
        Path file = Paths.state().resolve("state.json");
        if (!Files.exists(file)) return state;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : json.keySet()) {
                JsonObject o = json.getAsJsonObject(key);
                state.live.put(key, new Live(key, o.get("pid").getAsLong(), o.get("port").getAsInt(),
                        o.get("runDir").getAsString(), o.get("log").getAsString(), o.get("world").getAsString(),
                        o.get("startedAt").getAsLong()));
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[harness] ignoring unreadable state file: " + e);
        }
        return state;
    }

    public synchronized void save() {
        JsonObject json = new JsonObject();
        for (Live l : live.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("pid", l.pid());
            o.addProperty("port", l.port());
            o.addProperty("runDir", l.runDir());
            o.addProperty("log", l.log());
            o.addProperty("world", l.world());
            o.addProperty("startedAt", l.startedAt());
            json.add(l.node(), o);
        }
        try {
            Files.createDirectories(Paths.state());
            Files.writeString(Paths.state().resolve("state.json"), GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot write state: " + e, e);
        }
    }

    /** Drops entries whose process has died. Returns true when anything was removed. */
    public synchronized boolean prune() {
        boolean changed = live.entrySet().removeIf(e -> !e.getValue().alive());
        if (changed) save();
        return changed;
    }

    /** A snapshot: {@code up} adds and removes entries from several threads while it waits for clients. */
    public synchronized Map<String, Live> all() {
        return new LinkedHashMap<>(live);
    }

    public synchronized Optional<Live> get(String node) {
        return Optional.ofNullable(live.get(node));
    }

    public synchronized void put(Live l) {
        live.put(l.node(), l);
        save();
    }

    public synchronized void remove(String node) {
        live.remove(node);
        save();
    }
}
