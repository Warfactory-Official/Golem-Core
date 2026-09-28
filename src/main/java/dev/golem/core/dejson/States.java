package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blockstate files whose variants name full model ids rather than suffixes of one base, and
 * {@code multipart} blockstates.
 *
 * <p>{@link Dejson.Declaration} covers the regular case, where every state is the block's own model
 * with a suffix and a rotation. A pipe that assembles itself from an arm per connected side, or a
 * machine whose lit and unlit states point at two unrelated models, does not fit that — it is a list
 * of "when these properties hold, add this model", and that is what this says.
 *
 * <pre>{@code
 * States.multipart("hbm", "pipe_anchor")
 *       .part(When.of("facing", "down"), new Variant("hbm:block/pipe_anchor_down"))
 *       .part(When.of("facing", "up"), new Variant("hbm:block/pipe_anchor_up"))
 *       .register();
 * }</pre>
 */
public final class States {

    private static final Map<String, JsonObject> FILES = new ConcurrentHashMap<>();

    private States() {}

    /** A {@code variants} blockstate built key by key. */
    public static VariantFile variants(String namespace, String path) {
        return new VariantFile(namespace + ":" + path);
    }

    /** A {@code multipart} blockstate built part by part. */
    public static MultipartFile multipart(String namespace, String path) {
        return new MultipartFile(namespace + ":" + path);
    }

    /** Every blockstate file declared here, keyed by block id. */
    public static Map<String, JsonObject> files() {
        return Collections.unmodifiableMap(FILES);
    }

    static void reset() {
        FILES.clear();
    }

    private static void publish(String blockId, JsonObject json) {
        Models.requireId(blockId, "block");
        if (Dejson.declaration(blockId) != null || FILES.putIfAbsent(blockId, json) != null) {
            throw new DejsonException("blockstate " + blockId + " is declared twice");
        }
    }

    public static final class VariantFile {

        private final String blockId;
        private final Map<String, List<Variant>> variants = new LinkedHashMap<>();

        private VariantFile(String blockId) {
            this.blockId = blockId;
        }

        /** The variant (or, given several, the random pool) the state {@code key} selects. */
        public VariantFile variant(String key, Variant... pool) {
            if (pool.length == 0) {
                throw new DejsonException(blockId + " variant '" + key + "' names no model");
            }
            if (variants.putIfAbsent(key, List.of(pool)) != null) {
                throw new DejsonException(blockId + " variant '" + key + "' declared twice");
            }
            return this;
        }

        public void register() {
            if (variants.isEmpty()) {
                throw new DejsonException(blockId + " declares no variants");
            }
            JsonObject map = new JsonObject();
            variants.forEach((key, pool) -> map.add(key, pool(pool)));
            JsonObject json = new JsonObject();
            json.add("variants", map);
            publish(blockId, json);
        }
    }

    public static final class MultipartFile {

        private final String blockId;
        private final List<JsonObject> parts = new ArrayList<>();

        private MultipartFile(String blockId) {
            this.blockId = blockId;
        }

        /** A part applied in every state. */
        public MultipartFile part(Variant... apply) {
            return part(null, apply);
        }

        /** A part applied when {@code when} holds; {@code null} means always. */
        public MultipartFile part(When when, Variant... apply) {
            if (apply.length == 0) {
                throw new DejsonException(blockId + " has a multipart case that applies nothing");
            }
            JsonObject part = new JsonObject();
            if (when != null) part.add("when", when.toJson());
            part.add("apply", pool(List.of(apply)));
            parts.add(part);
            return this;
        }

        public void register() {
            if (parts.isEmpty()) {
                throw new DejsonException(blockId + " declares no multipart cases");
            }
            JsonArray list = new JsonArray();
            parts.forEach(part -> list.add(part.deepCopy()));
            JsonObject json = new JsonObject();
            json.add("multipart", list);
            publish(blockId, json);
        }
    }

    private static com.google.gson.JsonElement pool(List<Variant> pool) {
        if (pool.size() == 1) {
            return pool.get(0).toJson();
        }
        JsonArray array = new JsonArray();
        pool.forEach(variant -> array.add(variant.toJson()));
        return array;
    }
}
