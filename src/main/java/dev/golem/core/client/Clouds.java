package dev.golem.core.client;

import dev.golem.core.Ids;
import dev.golem.core.platform.Platform;
import dev.golem.core.render.CloudRendering;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Cloud replacements and native-first additions rendered through the node's cloud renderer.
 * Replacement shifts supply the whole drift; additions shift relative to the untouched native pass.
 * Native caches, texture reloads, fog and cloud settings retain their ownership.
 */
public final class Clouds {

    private Clouds() {}

    /**
     * One cloud layer.
     *
     * @param height world y of the layer's cloud bottoms, before the 0.33 lift every node's renderer adds (1.7's
     *               {@code getCloudHeight()}: 128 is 1.7's default)
     * @param red    colour of the top faces, 0..1 (1.7 {@code drawClouds}; sides and bottom are shaded from it)
     * @param green  as {@code red}
     * @param blue   as {@code red}
     * @param alpha  0..1 (vanilla's is 0.8)
     * @param shiftX blocks added to the camera's x before the cloud texture is looked up (scrolling and per-layer offsets)
     * @param shiftZ the same along z
     */
    public record Layer(float height, float red, float green, float blue, float alpha, double shiftX, double shiftZ) {}

    /** The layers to draw in a level this frame; an empty list draws no clouds. Called on the render thread. */
    @FunctionalInterface
    public interface Provider {
        List<Layer> layers(Level level, float partialTick);
    }

    /** NeoForge 26.1's custom-clouds id for Golem's renderer ({@code golemcore:clouds}). */
    public static final String NAMESPACE = "golemcore";
    public static final String PATH = "clouds";

    /** Extra layers drawn after the native first layer; the native call supplies its height, ARGB and clock. */
    @FunctionalInterface
    public interface AdditionalProvider {
        List<Layer> layers(Level level, NativeLayer nativeLayer);

        /** Whether the native first layer is visible. Older nodes have no cloud alpha environment attribute. */
        default boolean visible(Level level) { return true; }
    }

    /** Native cloud bottoms before the engine's 0.33 lift, and the exact input to its renderer this frame. */
    public record NativeLayer(float height, int argb, long gameTime, float partialTick) {}

    private record Registration(Provider replacement, AdditionalProvider additions) {}
    private static final Map<ResourceKey<Level>, Registration> PROVIDERS = new ConcurrentHashMap<>();

    /**
     * Replaces vanilla's clouds in {@code dimension} by {@code provider}'s layers. Call during client init. One provider
     * per dimension: a second registration for the same dimension (another mod's, or {@link #off}) is refused with an
     * {@link IllegalStateException} naming the dimension, never a silent replacement.
     */
    public static void register(ResourceKey<Level> dimension, Provider provider) {
        register(dimension, new Registration(provider, null));
    }

    /** Keeps vanilla's first layer and appends the provider's extra layers after it, using the native pass's inputs. */
    public static void registerAdditions(ResourceKey<Level> dimension, AdditionalProvider provider) {
        register(dimension, new Registration(null, provider));
    }

    private static void register(ResourceKey<Level> dimension, Registration registration) {
        var existing = PROVIDERS.putIfAbsent(dimension, registration);
        if (existing != null) {
            throw new IllegalStateException("The clouds of " + Ids.keyId(dimension) + " are already registered; "
                    + "one mod draws a dimension's clouds");
        }
        if (Platform.isClient()) CloudRendering.install(dimension, registration.replacement() != null);
    }

    /** No clouds at all in {@code dimension} (1.7 {@code getCloudHeight()} of -99999). */
    public static void off(ResourceKey<Level> dimension) {
        register(dimension, (level, partialTick) -> List.of());
    }

    /** Whether {@code level} is a client level whose clouds a {@link Provider} draws. */
    public static boolean has(Level level) {
        return level != null && level.isClientSide() && !PROVIDERS.isEmpty() && PROVIDERS.containsKey(level.dimension());
    }

    /** The layers {@code level}'s provider returns now; empty without one. */
    public static List<Layer> layers(Level level, float partialTick) {
        if (!has(level)) return List.of();
        var provider = PROVIDERS.get(level.dimension()).replacement();
        if (provider == null) return List.of();
        List<Layer> layers = provider.layers(level, partialTick);
        return layers == null ? List.of() : layers;
    }

    /** Whether this level replaces the native pass rather than adding layers after it. */
    public static boolean replaces(Level level) {
        return has(level) && PROVIDERS.get(level.dimension()).replacement() != null;
    }

    public static boolean additive(Level level) {
        return has(level) && PROVIDERS.get(level.dimension()).additions() != null;
    }

    public static boolean nativeVisible(Level level) {
        return !additive(level) || PROVIDERS.get(level.dimension()).additions().visible(level);
    }

    /** Extra layers only. Their shifts are relative to the native renderer's drift. */
    public static List<Layer> additions(Level level, NativeLayer nativeLayer) {
        return PROVIDERS.get(level.dimension()).additions().layers(level, nativeLayer);
    }

    /**
     * 26.1: the level's {@code visual/cloud_color} attribute as vanilla's cloud pass needs it for a registered level -
     * opaque while it has layers (the pass runs and the renderer draws them), transparent while it has none (vanilla adds
     * no pass). Additive providers preserve the native alpha unless the first layer is hidden. RGB remains unchanged.
     */
    public static int cloudColorAttribute(Level level, int argb) {
        if (additive(level)) return nativeVisible(level) ? argb : argb & 0x00FFFFFF;
        if (!replaces(level)) return argb;
        return layers(level, 0.0F).isEmpty() ? argb & 0x00FFFFFF : argb | 0xFF000000;
    }
}
