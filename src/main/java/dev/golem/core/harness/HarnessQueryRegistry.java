package dev.golem.core.harness;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;

/**
 * Extension point for consumers: extra {@code query} kinds answered on the client thread.
 *
 * <p>HBM can register, for example, {@code machine} to report a block entity's power and fluid
 * fields, without Golem Core knowing those types. Registration is a no-op when the harness is off.
 */
public final class HarnessQueryRegistry {

    private static final Map<String, BiFunction<Minecraft, JsonObject, JsonObject>> queries = new LinkedHashMap<>();

    private HarnessQueryRegistry() {}

    public static void register(String kind, BiFunction<Minecraft, JsonObject, JsonObject> query) {
        queries.put(kind, query);
    }

    static BiFunction<Minecraft, JsonObject, JsonObject> get(String kind) {
        return queries.get(kind);
    }

    static Iterable<String> kinds() {
        return queries.keySet();
    }
}
