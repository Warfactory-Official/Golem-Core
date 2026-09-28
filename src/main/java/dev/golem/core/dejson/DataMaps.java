package dev.golem.core.dejson;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NeoForge data maps: per-registry-entry values a loader reads from {@code data/neoforge/data_maps/}.
 *
 * <p>Only NeoForge reads these files; the other loaders get the same values through their own APIs
 * at registration, so a data map declared here is inert data on them, exactly as the authored file
 * was.
 *
 * <pre>{@code
 * DataMaps.furnaceFuels().burnTime("hbm:powder_coal", 1600).register();
 * }</pre>
 */
public final class DataMaps {

    private DataMaps() {}

    /** {@code neoforge:furnace_fuels}: item to burn time in ticks. */
    public static FurnaceFuels furnaceFuels() {
        return new FurnaceFuels();
    }

    public static final class FurnaceFuels {

        private final Map<String, Integer> values = new LinkedHashMap<>();

        private FurnaceFuels() {}

        public FurnaceFuels burnTime(String item, int ticks) {
            Models.requireId(item, "fuel item");
            if (ticks <= 0) {
                throw new DejsonException(item + " burns for " + ticks + " ticks; a fuel burns for some");
            }
            if (values.putIfAbsent(item, ticks) != null) {
                throw new DejsonException(item + " is given a burn time twice");
            }
            return this;
        }

        public JsonObject register() {
            JsonObject entries = new JsonObject();
            values.forEach((item, ticks) -> {
                JsonObject entry = new JsonObject();
                entry.addProperty("burn_time", ticks);
                entries.add(item, entry);
            });
            JsonObject json = new JsonObject();
            json.add("values", entries);
            return Data.declare("data/neoforge/data_maps/item/furnace_fuels.json", json);
        }
    }
}
