package dev.golem.core.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A dimension's rain and snow, the stand-in for 1.7's {@code WorldProvider.getWeatherRenderer()}: for a registered
 * dimension vanilla's weather columns are replaced, on every node, by the quads its {@link Renderer} hands over each
 * frame. Vanilla's rain splashes, rain sounds and lightning are untouched (1.7 drew those outside the weather renderer
 * too). Dimensions nobody registers keep vanilla's weather.
 *
 * <p>The quads are drawn with 1.7's weather state as hbm-space's {@code WeatherProviderCelestial} set it: the texture's
 * own colour is <b>not</b> used ({@code GL_COMBINE} with {@code GL_REPLACE} from the vertex colour), so a quad's colour is
 * its vertex colour times the light map at its light value, and its alpha is the texture's alpha times the vertex alpha.
 * Fragments at alpha 0.1 or below are discarded; blending is {@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA}, both faces, depth
 * tested without writing depth (with depth writes under Fabulous, as vanilla's weather), fogged like the world. Batches
 * draw in submission order, as 1.7's {@code tessellator.draw()} runs did.
 *
 * <p>How each node is hooked ({@code dev.golem.core.render.PrecipitationRendering}):
 * <ul>
 *   <li>26.1 NeoForge: NeoForge's {@code CustomWeatherEffectRenderer}, selected for the level by an attribute layer on
 *       {@code neoforge:visual/custom_weather_effects} ({@code MixinEnvironmentSkyLayers}).</li>
 *   <li>26.1 Fabric: {@code MixinWeatherRender} (Fabric 26.1 has no weather renderer hook).</li>
 *   <li>1.21.1 / 1.20.1 Fabric: Fabric API's {@code DimensionRenderingRegistry.registerWeatherRenderer}.</li>
 *   <li>1.21.1 NeoForge / 1.20.1 Forge: {@code MixinLegacyWeatherRender} at the head of
 *       {@code LevelRenderer.renderSnowAndRain}.</li>
 * </ul>
 * In each case the quads go into vanilla's weather pass (the weather target under Fabulous).
 *
 * <p>Client only: {@link #register} on a dedicated server stores nothing that is ever read.
 */
public final class Precipitation {

    private Precipitation() {}

    /** Draws a level's weather this frame into {@code frame}; called on the render thread, nothing drawn is nothing. */
    @FunctionalInterface
    public interface Renderer {
        void render(Frame frame);
    }

    /** One batch: a texture and its quads (see {@link Frame#quads}). */
    public record Batch(String texture, float[] vertices) {}

    /** One frame's weather draw: the level, the partial tick, the camera and the batches handed over. */
    public static final class Frame {
        private final ClientLevel level;
        private final float partialTick;
        private final Vec3 camera;
        private final List<Batch> batches = new ArrayList<>();

        Frame(ClientLevel level, float partialTick, Vec3 camera) {
            this.level = level;
            this.partialTick = partialTick;
            this.camera = camera;
        }

        public ClientLevel level() {
            return level;
        }

        public float partialTick() {
            return partialTick;
        }

        /** The camera's (interpolated) world position this frame. */
        public Vec3 camera() {
            return camera;
        }

        /**
         * How many columns out from the camera's column weather is drawn: 1.7's {@code fancyGraphics ? 10 : 5}. Before
         * 26.1 the same test ({@code Minecraft.useFancyGraphics()}); 26.1 has no fancy graphics and asks the player
         * instead ({@code options.weatherRadius()}, 10 by default).
         */
        public int radius() {
            //? if >=26.1 {
            return net.minecraft.client.Minecraft.getInstance().options.weatherRadius().get();
            //?} else {
            /*return net.minecraft.client.Minecraft.useFancyGraphics() ? 10 : 5;
            *///?}
        }

        /**
         * Quads with {@code texture} ({@code namespace:path} of an image): 10 floats per vertex, 4 vertices per quad -
         * the position relative to {@link #camera} {@code x y z} (1.7's {@code setTranslation(-camX, -camY, -camZ)}),
         * {@code u v}, colour {@code r g b a} (0..1) and the packed light value (1.7 {@code setBrightness}, block light
         * {@code << 4} or'd with sky light {@code << 20}; exact as a float).
         */
        public void quads(String texture, float... vertices) {
            if (vertices.length >= 40) batches.add(new Batch(texture, vertices));
        }

        /** What was handed over, in order (for the node's drawing code). */
        public List<Batch> batches() {
            return batches;
        }
    }

    private static final Map<ResourceKey<Level>, Renderer> RENDERERS = new ConcurrentHashMap<>();
    private static long quadsDrawn;

    /**
     * Replaces vanilla's rain and snow in {@code dimension} by {@code renderer}'s quads. Call during client init. One
     * renderer per dimension: a second registration for the same dimension (another mod's) is refused with an
     * {@link IllegalStateException} naming the dimension and the renderer already there, never a silent replacement.
     */
    public static void register(ResourceKey<Level> dimension, Renderer renderer) {
        Renderer existing = RENDERERS.putIfAbsent(dimension, renderer);
        if (existing != null) {
            throw new IllegalStateException("The weather of " + dev.golem.core.Ids.keyId(dimension) + " is already drawn by "
                    + existing.getClass().getName() + "; one mod draws a dimension's rain and snow");
        }
        if (dev.golem.core.platform.Platform.isClient()) dev.golem.core.render.PrecipitationRendering.install(dimension);
    }

    /** Whether {@code level} is a client level whose weather a {@link Renderer} draws. */
    public static boolean has(Level level) {
        return level != null && level.isClientSide() && !RENDERERS.isEmpty() && RENDERERS.containsKey(level.dimension());
    }

    /** Runs {@code level}'s renderer for one frame; null without one. For the node's drawing code. */
    public static Frame collect(ClientLevel level, float partialTick, Vec3 camera) {
        Renderer renderer = level == null ? null : RENDERERS.get(level.dimension());
        if (renderer == null) return null;
        Frame frame = new Frame(level, partialTick, camera);
        renderer.render(frame);
        return frame;
    }

    /** Counts drawn quads (for the node's drawing code). */
    public static void countDrawn(int quads) {
        quadsDrawn += quads;
    }

    /** How many weather quads Golem has drawn since start (a positive signal for tests that the node's hook runs). */
    public static long quadsDrawn() {
        return quadsDrawn;
    }
}
