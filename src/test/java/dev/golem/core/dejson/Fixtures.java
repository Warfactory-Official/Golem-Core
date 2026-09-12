package dev.golem.core.dejson;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The reference files these tests generate against, copied verbatim out of the mod's own
 * {@code assets/hbm} so a table can be checked against what is actually shipped rather than against
 * a restatement of the table being tested.
 */
final class Fixtures {

    private Fixtures() {}

    static JsonObject blockState(String name) {
        return read("/dejson/" + name + ".json");
    }

    static JsonObject model(String name) {
        return read("/dejson/models/" + name + ".json");
    }

    /** Parses a single-quoted abbreviation of JSON, so expectations read as one line. */
    static JsonObject json(String abbreviated) {
        return JsonParser.parseString(abbreviated.replace('\'', '"')).getAsJsonObject();
    }

    /**
     * Compares two models, treating a bare {@code block/cube_all} parent as the {@code minecraft:}
     * one it resolves to. Both spellings are on disk; the generator emits the namespaced form.
     */
    static void assertSameModel(JsonObject expected, JsonObject actual) {
        assertEquals(namespaced(expected.get("parent").getAsString()),
                namespaced(actual.get("parent").getAsString()), "parent");
        assertEquals(expected.getAsJsonObject("textures"), actual.getAsJsonObject("textures"),
                "textures");
    }

    private static String namespaced(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    private static JsonObject read(String path) {
        try (InputStream stream = Fixtures.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("missing fixture " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("could not read fixture " + path, failure);
        }
    }
}
