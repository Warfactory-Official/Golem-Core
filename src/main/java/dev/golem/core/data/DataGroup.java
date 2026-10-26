package dev.golem.core.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import java.util.Map;

/** One subsystem registry entry: named typed parameters retain their original encoded values. */
public record DataGroup(Map<String, Dynamic<?>> values) {
    public static final Codec<DataGroup> CODEC = Codec.unboundedMap(Codec.STRING, Codec.PASSTHROUGH)
            .xmap(DataGroup::new, DataGroup::values);
    public DataGroup { values = Map.copyOf(values); }
}
