package dev.golem.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.golem.core.Ids;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import net.minecraft.resources.ResourceKey;

/** Typed subsystem parameters. A complete incoming set is validated before any current value is published. */
public final class DataGroups {
    private DataGroups() {}

    private static final Codec<Integer> EXACT_INT = Codec.PASSTHROUGH.comapFlatMap(value ->
            value.asNumber().flatMap(number -> {
                try { return DataResult.success(new BigDecimal(number.toString()).intValueExact()); }
                catch (ArithmeticException | NumberFormatException error) {
                    return DataResult.error(() -> "Expected exact 32-bit integer: " + number);
                }
            }), value -> new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(value)));

    public static Group group(List<Group> groups,
                              //? if >=26.1 {
                              Identifier
                              //?} else {
                              /*ResourceLocation
                              *///?}
                              id) {
        if (groups.stream().anyMatch(group -> group.id.equals(id)))
            throw new IllegalStateException("Parameter group declared twice: " + id);
        var group = new Group(id);
        groups.add(group);
        return group;
    }

    /**
     * Native registry loading exposes the world-load snapshot through RegistryAccess. Current parameter values
     * come exclusively from DataFiles on startup, /reload and remote client sync: a reload must never republish
     * the native registry's frozen initial snapshot over newly read files.
     */
    public static void install(ResourceKey<Registry<DataGroup>> registry, List<Group> groups) {
        var id = Ids.keyId(registry);
        var folder = id.getNamespace() + "/" + id.getPath();
        DataFiles.folder(folder, files -> {
            //? if >=26.1 {
            var entries = new LinkedHashMap<Identifier, DataGroup>();
            //?} else {
            /*var entries = new LinkedHashMap<ResourceLocation, DataGroup>();
            *///?}
            files.forEach((name, value) -> entries.put(Ids.parse(name), decode(DataGroup.CODEC, value, name)));
            publish(entries, groups);
        });
    }

    /** Explicitly applies a caller-supplied registry snapshot; automatic refreshes use the DataFiles path above. */
    public static void apply(RegistryAccess registries, ResourceKey<Registry<DataGroup>> key, List<Group> groups) {
        Registry<DataGroup> registry;
        //? if >=1.21.2 {
        registry = registries.lookupOrThrow(key);
        //?} else {
        /*registry = registries.registryOrThrow(key);
        *///?}
        //? if >=26.1 {
        var entries = new LinkedHashMap<Identifier, DataGroup>();
        //?} else {
        /*var entries = new LinkedHashMap<ResourceLocation, DataGroup>();
        *///?}
        for (var id : registry.keySet()) entries.put(id, registry.getOptional(id).orElseThrow());
        publish(entries, groups);
    }

    //? if >=26.1 {
    private static void publish(Map<Identifier, DataGroup> entries, List<Group> groups) {
    //?} else {
    /*private static void publish(Map<ResourceLocation, DataGroup> entries, List<Group> groups) {
    *///?}
        var unknown = new HashSet<>(entries.keySet());
        var resolved = new ArrayList<Map<String, Object>>(groups.size());
        for (var group : groups) {
            unknown.remove(group.id);
            var entry = entries.get(group.id);
            if (entry == null) throw new IllegalStateException("Missing parameter group " + group.id);
            resolved.add(group.resolve(entry));
        }
        if (!unknown.isEmpty()) throw new IllegalStateException("Undeclared parameter groups " + unknown);
        for (var index = 0; index < groups.size(); index++) groups.get(index).publish(resolved.get(index));
    }

    private static <T> T decode(Codec<T> codec, JsonElement value, String name) {
        var result = codec.parse(JsonOps.INSTANCE, value);
        return result.result().orElseThrow(() -> new IllegalStateException(name + ": "
                + result.error().map(error -> error.message()).orElse("No decoded value")));
    }

    private static <T> Codec<T> validated(Codec<T> codec, Predicate<T> predicate, String description) {
        return codec.flatXmap(value -> predicate.test(value) ? DataResult.success(value)
                : DataResult.error(() -> description + ": " + value), value -> predicate.test(value)
                ? DataResult.success(value) : DataResult.error(() -> description + ": " + value));
    }

    public static final class Group {
        private final List<Param<?>> params = new ArrayList<>();
        //? if >=26.1 {
        private final Identifier id;
        private Group(Identifier id) { this.id = id; }
        public Identifier id() { return id; }
        //?} else {
        /*private final ResourceLocation id;
        private Group(ResourceLocation id) { this.id = id; }
        public ResourceLocation id() { return id; }
        *///?}

        public Map<String, Dynamic<?>> defaults() {
            var values = new LinkedHashMap<String, Dynamic<?>>();
            for (var param : params) values.put(param.key, param.encodedDefault());
            return values;
        }

        private Map<String, Object> resolve(DataGroup entry) {
            var unknown = new HashSet<>(entry.values().keySet());
            var resolved = new LinkedHashMap<String, Object>();
            for (var param : params) {
                unknown.remove(param.key);
                var encoded = entry.values().get(param.key);
                resolved.put(param.key, encoded == null ? param.defaultValue
                        : decode(param.codec, encoded.convert(JsonOps.INSTANCE).getValue(), id + " " + param.key));
            }
            if (!unknown.isEmpty()) throw new IllegalStateException(id + " undeclared parameters " + unknown);
            return resolved;
        }

        @SuppressWarnings("unchecked")
        private void publish(Map<String, Object> values) {
            for (var param : params) ((Param<Object>) param).value = values.get(param.key);
        }

        private <T> Param<T> add(String key, T initial, Codec<T> codec) {
            if (params.stream().anyMatch(param -> param.key.equals(key)))
                throw new IllegalStateException(id + " parameter declared twice: " + key);
            var param = new Param<>(key, initial, codec);
            params.add(param);
            return param;
        }

        public Param<Boolean> bool(String key, boolean initial) { return add(key, initial, Codec.BOOL); }
        public Param<Integer> integer(String key, int initial) { return bounded(key, initial, 0, Integer.MAX_VALUE); }
        public Param<Integer> bounded(String key, int initial, int minimum, int maximum) {
            return add(key, initial, validated(EXACT_INT, value -> value >= minimum && value <= maximum,
                    "Expected integer in [" + minimum + ", " + maximum + "]"));
        }
        public Param<Double> real(String key, double initial) { return bounded(key, initial, 0D, Double.MAX_VALUE); }
        public Param<Double> chance(String key, double initial) { return bounded(key, initial, 0D, 1D); }
        private Param<Double> bounded(String key, double initial, double minimum, double maximum) {
            return add(key, initial, validated(Codec.DOUBLE,
                    value -> Double.isFinite(value) && value >= minimum && value <= maximum,
                    "Expected finite number in [" + minimum + ", " + maximum + "]"));
        }
    }

    public static final class Param<T> {
        private final String key;
        private final T defaultValue;
        private final Codec<T> codec;
        private volatile T value;
        private Param(String key, T defaultValue, Codec<T> codec) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.codec = codec;
            this.value = defaultValue;
        }
        private Dynamic<?> encodedDefault() {
            var result = codec.encodeStart(JsonOps.INSTANCE, defaultValue);
            return new Dynamic<>(JsonOps.INSTANCE, result.result().orElseThrow(() ->
                    new IllegalStateException("Invalid declared default " + key + ": " + defaultValue)));
        }
        public String key() { return key; }
        public T defaultValue() { return defaultValue; }
        public T get() { return value; }
        /** Applies a runtime value only when the declared codec accepts it. */
        public void set(T value) {
            codec.encodeStart(JsonOps.INSTANCE, value).result().orElseThrow(() ->
                    new IllegalArgumentException("Invalid value for " + key + ": " + value));
            this.value = value;
        }
    }
}
