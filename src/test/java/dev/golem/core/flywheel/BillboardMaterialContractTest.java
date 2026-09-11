package dev.golem.core.flywheel;

//? if neoforge && >=26.1 {

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BillboardMaterialContractTest {

    @Test
    void cloudletsDrawBeforeAdditiveFlareWithRequiredState() {
        Material cloudlets = Material.TRANSLUCENT_SORTED_NO_CULL;
        Material flare = Material.SOURCE_ALPHA_ADDITIVE_NO_CULL;

        assertTrue(cloudlets.stage == Material.Stage.TRANSLUCENT);
        assertTrue(cloudlets.depthWrite);
        assertFalse(cloudlets.cull);
        assertTrue(cloudlets.requiresDepthSort);

        assertTrue(flare.stage == Material.Stage.TRANSLUCENT);
        assertFalse(flare.depthWrite);
        assertFalse(flare.cull);
        assertTrue(flare.disableLighting);
        assertFalse(flare.requiresDepthSort);

        assertTrue(cloudlets.ordinal() < flare.ordinal());
    }
}
//?}
