package dev.golem.core.gemrender;

import dev.golem.core.client.DynamicLights;
import java.util.List;

/** A node with no GemRender build: dynamic lights are never shaded, so their fallbacks draw. */
public final class GemLights {

    private GemLights() {}

    public static boolean shading() {
        return false;
    }

    public static void bind(List<DynamicLights.Provider> providers) {}
}
