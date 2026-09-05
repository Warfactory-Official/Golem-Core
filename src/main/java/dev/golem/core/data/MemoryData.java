package dev.golem.core.data;

import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/** In-memory persistence representation used by compatibility-level contract tests. */
final class MemoryData {
    private final Map<String, Object> values = new HashMap<>();

    DataOut writer() { return new Writer(this); }
    DataIn reader() { return new Reader(this); }

    void saveItems(NonNullList<ItemStack> stacks, boolean alsoWhenEmpty) {
        if (alsoWhenEmpty || stacks.stream().anyMatch(stack -> !stack.isEmpty())) {
            values.put("Items", stacks.stream().map(ItemStack::copy).toList());
        }
    }

    void loadItems(NonNullList<ItemStack> stacks) {
        if (!(values.get("Items") instanceof List<?> saved)) return;
        for (int i = 0; i < stacks.size() && i < saved.size(); i++) {
            if (saved.get(i) instanceof ItemStack stack) stacks.set(i, stack.copy());
        }
    }

    interface Access {
        MemoryData data();
    }

    private record Writer(MemoryData data) implements DataOut, Access {
        @Override public void putBoolean(String name, boolean value) { data.values.put(name, value); }
        @Override public void putByte(String name, byte value) { data.values.put(name, value); }
        @Override public void putShort(String name, short value) { data.values.put(name, value); }
        @Override public void putInt(String name, int value) { data.values.put(name, value); }
        @Override public void putLong(String name, long value) { data.values.put(name, value); }
        @Override public void putFloat(String name, float value) { data.values.put(name, value); }
        @Override public void putDouble(String name, double value) { data.values.put(name, value); }
        @Override public void putString(String name, String value) { data.values.put(name, value); }
        @Override public void putIntArray(String name, int[] value) { data.values.put(name, value.clone()); }
        @Override public <T> void store(String name, Codec<T> codec, T value) { data.values.put(name, value); }
        @Override public <T> void storeNullable(String name, Codec<T> codec, T value) {
            if (value != null) data.values.put(name, value);
        }
        @Override public DataOut child(String name) {
            MemoryData child = new MemoryData();
            data.values.put(name, child);
            return child.writer();
        }
        @Override public ChildList childrenList(String name) {
            List<MemoryData> children = new ArrayList<>();
            data.values.put(name, children);
            return () -> {
                MemoryData child = new MemoryData();
                children.add(child);
                return child.writer();
            };
        }
        @Override public void discard(String name) { data.values.remove(name); }
    }

    private record Reader(MemoryData data) implements DataIn, Access {
        @Override public boolean getBooleanOr(String name, boolean fallback) {
            return data.values.get(name) instanceof Boolean value ? value : fallback;
        }
        @Override public byte getByteOr(String name, byte fallback) {
            return data.values.get(name) instanceof Number value ? value.byteValue() : fallback;
        }
        @Override public short getShortOr(String name, short fallback) {
            return data.values.get(name) instanceof Number value ? value.shortValue() : fallback;
        }
        @Override public int getIntOr(String name, int fallback) {
            return data.values.get(name) instanceof Number value ? value.intValue() : fallback;
        }
        @Override public long getLongOr(String name, long fallback) {
            return data.values.get(name) instanceof Number value ? value.longValue() : fallback;
        }
        @Override public float getFloatOr(String name, float fallback) {
            return data.values.get(name) instanceof Number value ? value.floatValue() : fallback;
        }
        @Override public double getDoubleOr(String name, double fallback) {
            return data.values.get(name) instanceof Number value ? value.doubleValue() : fallback;
        }
        @Override public String getStringOr(String name, String fallback) {
            return data.values.get(name) instanceof String value ? value : fallback;
        }
        @Override public Optional<int[]> getIntArray(String name) {
            return data.values.get(name) instanceof int[] value ? Optional.of(value.clone()) : Optional.empty();
        }
        @Override @SuppressWarnings("unchecked") public <T> Optional<T> read(String name, Codec<T> codec) {
            return Optional.ofNullable((T) data.values.get(name));
        }
        @Override public Optional<DataIn> child(String name) {
            return data.values.get(name) instanceof MemoryData value ? Optional.of(value.reader()) : Optional.empty();
        }
        @Override public DataIn childOrEmpty(String name) {
            return child(name).orElseGet(() -> new MemoryData().reader());
        }
        @Override public List<DataIn> childrenListOrEmpty(String name) {
            if (!(data.values.get(name) instanceof List<?> list)) return List.of();
            return list.stream().filter(MemoryData.class::isInstance).map(MemoryData.class::cast)
                    .map(MemoryData::reader).toList();
        }
    }
}
