package dev.golem.core.data;

//? if >=26.1 {
import com.mojang.serialization.Codec;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** {@link DataOut} backed by 26.1's {@code ValueOutput}. Pure delegation — the shapes already match. */
public record ValueDataOut(ValueOutput delegate) implements DataOut {

    @Override
    public void putBoolean(String name, boolean value) {
        delegate.putBoolean(name, value);
    }

    @Override
    public void putByte(String name, byte value) {
        delegate.putByte(name, value);
    }

    @Override
    public void putShort(String name, short value) {
        delegate.putShort(name, value);
    }

    @Override
    public void putInt(String name, int value) {
        delegate.putInt(name, value);
    }

    @Override
    public void putLong(String name, long value) {
        delegate.putLong(name, value);
    }

    @Override
    public void putFloat(String name, float value) {
        delegate.putFloat(name, value);
    }

    @Override
    public void putDouble(String name, double value) {
        delegate.putDouble(name, value);
    }

    @Override
    public void putString(String name, String value) {
        delegate.putString(name, value);
    }

    @Override
    public void putIntArray(String name, int[] value) {
        delegate.putIntArray(name, value);
    }

    @Override
    public <T> void store(String name, Codec<T> codec, T value) {
        delegate.store(name, codec, value);
    }

    @Override
    public <T> void storeNullable(String name, Codec<T> codec, @Nullable T value) {
        delegate.storeNullable(name, codec, value);
    }

    @Override
    public DataOut child(String name) {
        return new ValueDataOut(delegate.child(name));
    }

    @Override
    public ChildList childrenList(String name) {
        ValueOutput.ValueOutputList list = delegate.childrenList(name);
        return () -> new ValueDataOut(list.addChild());
    }

    @Override
    public void discard(String name) {
        delegate.discard(name);
    }
}
//?}
