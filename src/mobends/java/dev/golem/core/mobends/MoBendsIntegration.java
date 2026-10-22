package dev.golem.core.mobends;

import dev.golem.core.GolemCore;
import dev.golem.core.render.BodyAnimations;
import goblinbob.mobends.api.skeleton.MoBendsAPI;

/** Mo' Bends answers {@link BodyAnimations}; the loader roots call {@link #setup} when it is loaded. */
public final class MoBendsIntegration {

    private MoBendsIntegration() {}

    public static void setup() {
        BodyAnimations.install(MoBendsAPI::isAnimated);
        GolemCore.LOG.info("Mo' Bends present: humanoid layers follow its skeleton");
    }
}
