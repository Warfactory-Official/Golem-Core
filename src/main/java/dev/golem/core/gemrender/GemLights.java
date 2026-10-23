package dev.golem.core.gemrender;

import com.wf.gemrender.light.LightCookie;
import com.wf.gemrender.light.LightSink;
import com.wf.gemrender.light.Lights;
import dev.golem.core.Ids;
import dev.golem.core.client.DynamicLights;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/** {@link DynamicLights} on GemRender's {@code Lights}. Load it only after {@code Instancing.isAvailable}. */
public final class GemLights {

    private GemLights() {}

    /** 0.1.14 ships its light classes on 1.21.1 NeoForge only. */
    public static boolean supported() {
        return !GemRender.UNFIXED;
    }

    public static boolean shading() {
        return Lights.shading();
    }

    public static void bind(List<DynamicLights.Provider> providers) {
        Adapter adapter = new Adapter();
        Lights.register((sink, partialTick) -> {
            adapter.sink = sink;
            for (DynamicLights.Provider provider : providers) provider.collect(adapter, partialTick);
        });
    }

    private static final class Adapter implements DynamicLights.Sink {

        private final Map<DynamicLights.Cookie, LightCookie> cookies = new HashMap<>();
        private LightSink sink;

        @Override
        public void point(double x, double y, double z, float range, int rgb, float intensity, boolean shadow) {
            sink.point(x, y, z, range, rgb, intensity, shadow);
        }

        @Override
        public void spot(double x, double y, double z, Vector3fc direction, Vector3fc up, float range,
                         float innerDegrees, float outerDegrees, int rgb, float intensity,
                         DynamicLights.@Nullable Cookie cookie, boolean shadow, float softness) {
            LightCookie gem = cookie == null ? null
                    : cookies.computeIfAbsent(cookie, c -> Lights.cookie(Ids.of(c.namespace(), c.path())));
            sink.spot(x, y, z, direction, up, range, innerDegrees, outerDegrees, rgb, intensity, gem, shadow, false,
                    softness);
        }
    }
}
