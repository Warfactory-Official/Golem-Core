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
 * A dimension's clouds, the stand-in for 1.7's {@code WorldProvider.getCloudRenderer()} / {@code getCloudHeight()}: for a
 * registered dimension vanilla's clouds are replaced, on every node, by the layers its {@link Provider} returns each
 * frame (none at all when the list is empty, see {@link #off}). Dimensions nobody registers keep vanilla's clouds.
 *
 * <p>Each {@link Layer} is drawn by the node's own vanilla cloud renderer with the layer's height, colour, alpha and
 * texture position, so it keeps vanilla's cloud texture, cells, face shading, depth pre-pass, Fabulous render target,
 * fog and the player's cloud setting. Vanilla's own drift ({@code ticks * 0.03} along x) is <b>not</b> added: the
 * layer's {@link Layer#shiftX shift} is the whole texture offset, so every node draws a layer at the same place for
 * the same inputs. Layers draw in list order.
 *
 * <p>How each node is hooked ({@code dev.golem.core.render.CloudRendering}):
 * <ul>
 *   <li>26.1 NeoForge: NeoForge's {@code CustomCloudsRenderer}, selected for the level by an attribute layer on
 *       {@code neoforge:visual/custom_clouds} ({@code MixinEnvironmentSkyLayers}).</li>
 *   <li>26.1 Fabric: {@code MixinCloudRender} (Fabric 26.1 has no cloud renderer hook).</li>
 *   <li>Both 26.1 loaders: vanilla adds no cloud pass when the {@code visual/cloud_color} alpha is 0, so the same attribute
 *       layer makes it opaque while a registered dimension has layers and transparent while it has none.</li>
 *   <li>1.21.1 / 1.20.1 Fabric: Fabric API's {@code DimensionRenderingRegistry.registerCloudRenderer}.</li>
 *   <li>1.21.1 NeoForge / 1.20.1 Forge: {@code MixinLegacyCloudRender} at the head of {@code LevelRenderer.renderClouds}.</li>
 * </ul>
 *
 * <p>Client only: {@link #register} on a dedicated server stores nothing that is ever read.
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

    private static final Map<ResourceKey<Level>, Provider> PROVIDERS = new ConcurrentHashMap<>();

    /**
     * Replaces vanilla's clouds in {@code dimension} by {@code provider}'s layers. Call during client init. One provider
     * per dimension: a second registration for the same dimension (another mod's, or {@link #off}) is refused with an
     * {@link IllegalStateException} naming the dimension and the provider already there, never a silent replacement.
     */
    public static void register(ResourceKey<Level> dimension, Provider provider) {
        Provider existing = PROVIDERS.putIfAbsent(dimension, provider);
        if (existing != null) {
            throw new IllegalStateException("The clouds of " + Ids.keyId(dimension) + " are already drawn by "
                    + existing.getClass().getName() + "; one mod draws a dimension's clouds");
        }
        if (Platform.isClient()) CloudRendering.install(dimension);
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
        List<Layer> layers = PROVIDERS.get(level.dimension()).layers(level, partialTick);
        return layers == null ? List.of() : layers;
    }

    /**
     * 26.1: the level's {@code visual/cloud_color} attribute as vanilla's cloud pass needs it for a registered level -
     * opaque while it has layers (the pass runs and the renderer draws them), transparent while it has none (vanilla adds
     * no pass). Unchanged for every other level. The RGB is never used for a registered level's layers.
     */
    public static int cloudColorAttribute(Level level, int argb) {
        if (!has(level)) return argb;
        return layers(level, 0.0F).isEmpty() ? argb & 0x00FFFFFF : argb | 0xFF000000;
    }
}
