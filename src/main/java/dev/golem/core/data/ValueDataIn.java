package dev.golem.core.data;

//? if >=26.1 {
import com.mojang.serialization.Codec;
import net.minecraft.world.level.storage.ValueInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** {@link DataIn} backed by 26.1's {@code ValueInput}. Pure delegation — the shapes already match. */
public record ValueDataIn(ValueInput delegate) implements DataIn {

    @Override
    public boolean getBooleanOr(String name, boolean defaultValue) {
        return delegate.getBooleanOr(name, defaultValue);
    }

    @Override
    public byte getByteOr(String name, byte defaultValue) {
        return delegate.getByteOr(name, defaultValue);
    }

    @Override
    public short getShortOr(String name, short defaultValue) {
        // Vanilla declares this as returning int despite taking a short default; narrow it back so the
        // facade stays symmetric with putShort.
        return (short) delegate.getShortOr(name, defaultValue);
    }

    @Override
    public int getIntOr(String name, int defaultValue) {
        return delegate.getIntOr(name, defaultValue);
    }

    @Override
    public long getLongOr(String name, long defaultValue) {
        return delegate.getLongOr(name, defaultValue);
    }

    @Override
    public float getFloatOr(String name, float defaultValue) {
        return delegate.getFloatOr(name, defaultValue);
    }

    @Override
    public double getDoubleOr(String name, double defaultValue) {
        return delegate.getDoubleOr(name, defaultValue);
    }

    @Override
    public String getStringOr(String name, String defaultValue) {
        return delegate.getStringOr(name, defaultValue);
    }

    @Override
    public Optional<int[]> getIntArray(String name) {
        return delegate.getIntArray(name);
    }

    @Override
    public <T> Optional<T> read(String name, Codec<T> codec) {
        return delegate.read(name, codec);
    }

    @Override
    public Optional<DataIn> child(String name) {
        return delegate.child(name).map(ValueDataIn::new);
    }

    @Override
    public DataIn childOrEmpty(String name) {
        return new ValueDataIn(delegate.childOrEmpty(name));
    }

    @Override
    public List<DataIn> childrenListOrEmpty(String name) {
        List<DataIn> out = new ArrayList<>();
        for (ValueInput child : delegate.childrenListOrEmpty(name)) {
            out.add(new ValueDataIn(child));
        }
        return out;
    }
}
//?}
