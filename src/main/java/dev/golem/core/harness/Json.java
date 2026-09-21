package dev.golem.core.harness;

import com.google.gson.JsonObject;

/** Small reply helpers. */
final class Json {

    private Json() {}

    static JsonObject ok() {
        JsonObject json = new JsonObject();
        json.addProperty("ok", true);
        return json;
    }

    static JsonObject error(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("ok", false);
        json.addProperty("error", message);
        return json;
    }

    static String str(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsInt() : fallback;
    }

    static double number(JsonObject json, String key, double fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsDouble() : fallback;
    }

    static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsBoolean() : fallback;
    }
}
