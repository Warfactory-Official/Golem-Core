package dev.golem.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** {@link WorldRules}: how stored text reads as a typed, clamped value, and that the saved map round-trips. */
class WorldRulesTest {

    @Test
    void doubleClampsWhenRead() {
        WorldRules.DoubleRule rule = new WorldRules.DoubleRule("t", 0.5D, 0D, 1D);
        assertEquals(1D, rule.parse("7.5"));
        assertEquals(0D, rule.parse("-1"));
        assertEquals(0.25D, rule.parse(" 0.25 "));
        assertNull(rule.parse("fast"));
        assertNull(rule.parse("NaN"));
    }

    @Test
    void intClampsWhenRead() {
        WorldRules.IntRule rule = new WorldRules.IntRule("t", 4, 2, 16);
        assertEquals(16, rule.parse("99"));
        assertEquals(2, rule.parse("1"));
        assertEquals(6, rule.parse("6"));
        assertNull(rule.parse("6.5"));
    }

    @Test
    void boolReadsOnlyTrueAndFalse() {
        WorldRules.BoolRule rule = new WorldRules.BoolRule("t", false);
        assertEquals(Boolean.TRUE, rule.parse("true"));
        assertEquals(Boolean.FALSE, rule.parse("false"));
        assertNull(rule.parse("1"));
    }

    @Test
    void savedValuesRoundTrip() {
        WorldRules.Data data = new WorldRules.Data();
        data.values.put("dialControlSpeed", "2.5");
        data.values.put("dialDisableMeltdowns", "true");
        Tag tag = WorldRules.Data.CODEC.encodeStart(NbtOps.INSTANCE, data).result().orElseThrow();
        WorldRules.Data back = WorldRules.Data.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
        assertEquals(data.values, back.values);
    }
}
