package dev.golem.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link Attributes#raiseMax}: the bound is raised through the widened field (so the access widener or
 * transformer is in effect on this node), the clamp follows it, and it never moves down.
 */
class AttributesRaiseMaxTest {

    /** {@code Attribute}'s static codec reads the attribute registry. */
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RangedAttribute fresh() {
        return new RangedAttribute("attribute.name.golem_test", 1.0D, 0.0D, 1024.0D);
    }

    @Test
    void raisesTheBoundAndTheClamp() {
        RangedAttribute attribute = fresh();
        assertEquals(1024.0D, attribute.sanitizeValue(20000.0D), "vanilla clamp before the raise");
        assertEquals(1_000_000.0D, Attributes.raiseMax(attribute, 1_000_000.0D));
        assertEquals(1_000_000.0D, attribute.getMaxValue());
        assertEquals(20000.0D, attribute.sanitizeValue(20000.0D), "the clamp follows the raised bound");
        assertEquals(0.0D, attribute.sanitizeValue(-5.0D), "the minimum is untouched");
    }

    @Test
    void isIdempotentAndNeverLowers() {
        RangedAttribute attribute = fresh();
        Attributes.raiseMax(attribute, 5000.0D);
        assertEquals(5000.0D, Attributes.raiseMax(attribute, 5000.0D), "a repeat is a no-op");
        assertEquals(5000.0D, Attributes.raiseMax(attribute, 2048.0D), "a lower request leaves a higher bound");
        assertEquals(1024.0D, Attributes.raiseMax(fresh(), 10.0D), "below the vanilla bound: unchanged");
    }

    @Test
    void rejectsNonRangedAndInvalidBounds() {
        Attribute plain = new Attribute("attribute.name.golem_plain", 0.0D) {};
        assertThrows(IllegalArgumentException.class, () -> Attributes.raiseMax(plain, 10.0D));
        assertThrows(IllegalArgumentException.class, () -> Attributes.raiseMax(fresh(), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Attributes.raiseMax(fresh(), -1.0D));
    }
}
