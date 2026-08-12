package dev.golem.core.data;

//? if <26.1 {
/*import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/^*
 * Behaviour tests for the pre-26.1 persistence backend.
 *
 * <p>This is the part of the compat layer with real translation logic rather than delegation, and it
 * is reachable without a game instance — {@code CompoundTag} and {@code NbtOps} are plain classes. The
 * cases that matter are not the happy-path round trips but the two places where the old
 * {@code CompoundTag} API and 26.1's {@code ValueInput} disagree: a missing key and a wrong-typed key.
 * On the old API both silently yield zero/empty; a backport that inherits that would corrupt data in a
 * way no compiler or GameTest would catch.
 ^/
class TagDataRoundTripTest {

    private static DataOut out(CompoundTag tag) {
        // Null registries: these cases use codecs that touch no registries, so plain NbtOps applies.
        return new TagDataOut(tag, null);
    }

    private static DataIn in(CompoundTag tag) {
        return new TagDataIn(tag, null);
    }

    @Test
    void scalarsRoundTrip() {
        CompoundTag tag = new CompoundTag();
        DataOut o = out(tag);
        o.putBoolean("bool", true);
        o.putByte("byte", (byte) -7);
        o.putShort("short", (short) 1234);
        o.putInt("int", 987654);
        o.putLong("long", 1234567890123L);
        o.putFloat("float", 2.5F);
        o.putDouble("double", -3.25D);
        o.putString("string", "hbm");
        o.putIntArray("ints", new int[] {1, 2, 3});

        DataIn i = in(tag);
        assertEquals(true, i.getBooleanOr("bool", false));
        assertEquals((byte) -7, i.getByteOr("byte", (byte) 0));
        assertEquals((short) 1234, i.getShortOr("short", (short) 0));
        assertEquals(987654, i.getIntOr("int", 0));
        assertEquals(1234567890123L, i.getLongOr("long", 0L));
        assertEquals(2.5F, i.getFloatOr("float", 0F));
        assertEquals(-3.25D, i.getDoubleOr("double", 0D));
        assertEquals("hbm", i.getStringOr("string", ""));
        assertTrue(i.getIntArray("ints").isPresent());
        assertEquals(3, i.getIntArray("ints").get().length);
    }

    /^*
     * The regression this backend exists to prevent. Old {@code CompoundTag.getInt} returns 0 for an
     * absent key, so a naive implementation would hand back 0 instead of the caller's default.
     ^/
    @Test
    void missingKeysYieldTheCallersDefaultNotZero() {
        DataIn i = in(new CompoundTag());
        assertEquals(true, i.getBooleanOr("nope", true));
        assertEquals((byte) 9, i.getByteOr("nope", (byte) 9));
        assertEquals((short) 9, i.getShortOr("nope", (short) 9));
        assertEquals(42, i.getIntOr("nope", 42));
        assertEquals(42L, i.getLongOr("nope", 42L));
        assertEquals(1.5F, i.getFloatOr("nope", 1.5F));
        assertEquals(1.5D, i.getDoubleOr("nope", 1.5D));
        assertEquals("fallback", i.getStringOr("nope", "fallback"));
        assertTrue(i.getIntArray("nope").isEmpty());
    }

    /^* Same trap via a different route: a key that exists but holds the wrong tag type. ^/
    @Test
    void wrongTypedKeysYieldTheCallersDefault() {
        CompoundTag tag = new CompoundTag();
        tag.putString("int", "not an int");
        tag.putInt("string", 5);

        DataIn i = in(tag);
        assertEquals(42, i.getIntOr("int", 42));
        assertEquals("fallback", i.getStringOr("string", "fallback"));
    }

    @Test
    void childRoundTripsAndIsAlwaysCreated() {
        CompoundTag tag = new CompoundTag();
        out(tag).child("inner").putInt("v", 7);

        assertEquals(7, in(tag).childOrEmpty("inner").getIntOr("v", 0));
        assertTrue(in(tag).child("inner").isPresent());

        // A child written but never populated must still exist, matching ValueOutput.child.
        CompoundTag empty = new CompoundTag();
        out(empty).child("blank");
        assertTrue(in(empty).child("blank").isPresent());
    }

    @Test
    void absentChildIsEmptyButChildOrEmptyStillReads() {
        DataIn i = in(new CompoundTag());
        assertTrue(i.child("nope").isEmpty());
        // childOrEmpty must not throw, and must fall through to defaults.
        assertEquals(3, i.childOrEmpty("nope").getIntOr("v", 3));
    }

    @Test
    void childrenListRoundTripsInOrder() {
        CompoundTag tag = new CompoundTag();
        DataOut.ChildList list = out(tag).childrenList("slots");
        list.addChild().putInt("i", 0);
        list.addChild().putInt("i", 1);
        list.addChild().putInt("i", 2);

        List<DataIn> read = in(tag).childrenListOrEmpty("slots");
        assertEquals(3, read.size());
        for (int n = 0; n < 3; n++) {
            assertEquals(n, read.get(n).getIntOr("i", -1));
        }
    }

    @Test
    void absentChildrenListIsEmpty() {
        assertTrue(in(new CompoundTag()).childrenListOrEmpty("nope").isEmpty());
    }

    @Test
    void discardRemovesTheKey() {
        CompoundTag tag = new CompoundTag();
        DataOut o = out(tag);
        o.putInt("gone", 1);
        o.discard("gone");
        assertEquals(-1, in(tag).getIntOr("gone", -1));
    }

    // A small codec with no registry dependencies, exercising store/read.
    private record Pair(int a, String b) {}

    private static final Codec<Pair> PAIR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("a").forGetter(Pair::a),
            Codec.STRING.fieldOf("b").forGetter(Pair::b)
    ).apply(instance, Pair::new));

    @Test
    void codecRoundTrips() {
        CompoundTag tag = new CompoundTag();
        out(tag).store("pair", PAIR_CODEC, new Pair(5, "x"));

        Optional<Pair> read = in(tag).read("pair", PAIR_CODEC);
        assertTrue(read.isPresent());
        assertEquals(new Pair(5, "x"), read.get());
    }

    @Test
    void storeNullableWritesNothingForNull() {
        CompoundTag tag = new CompoundTag();
        out(tag).storeNullable("pair", PAIR_CODEC, null);
        assertTrue(in(tag).read("pair", PAIR_CODEC).isEmpty());
    }

    /^*
     * A malformed value must not blow up the read. 26.1's {@code ValueInput.read} reports and returns
     * empty; throwing here would abort a block entity's whole load.
     ^/
    @Test
    void malformedCodecValueReturnsEmptyRatherThanThrowing() {
        CompoundTag tag = new CompoundTag();
        tag.putString("pair", "definitely not a pair");

        Optional<Pair> read = in(tag).read("pair", PAIR_CODEC);
        assertNotNull(read);
        assertFalse(read.isPresent());
    }
}
*///?}
