package dev.golem.core.render;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;

/** Optional camera-facing billboard instances without exposing an engine implementation. */
public final class Billboards {

    @FunctionalInterface
    public interface RowSource {
        void write(RowWriter writer);
    }

    @FunctionalInterface
    public interface RowWriter {
        void row(double x, double y, double z, float halfSize,
                 float red, float green, float blue, float alpha, int packedLight);
    }

    public record Frame(float partialTick, Vec3 cameraPosition, Quaternionfc cameraOrientation) {}

    @FunctionalInterface
    public interface Source<T extends Entity> {
        void write(T entity, Frame frame, RowWriter writer);
    }

    public enum Mode {
        DEPTH_WRITING_TRANSLUCENT,
        ADDITIVE,
        SOURCE_ALPHA_ADDITIVE
    }

    public record Layer(String texture, Mode mode) {
        public Layer {
            if (texture == null || texture.isBlank()) throw new IllegalArgumentException("Blank billboard texture");
            Objects.requireNonNull(mode, "mode");
        }
    }

    @FunctionalInterface
    public interface LayerWriter {
        RowWriter layer(int index);
    }

    @FunctionalInterface
    public interface LayeredSource<T extends Entity> {
        void write(T entity, Frame frame, LayerWriter writer);
    }

    private Billboards() {}

    /** Monotonic client render-frame id where the vendored provider exists, otherwise -1. */
    public static int frameId() {
        //? if neoforge && >=26.1 {
        return dev.golem.core.flywheel.visual.GolemRenderSystem.currentFrameId();
        //?} else {
        /*return -1;
        *///?}
    }

    /** Registers an entity type whose billboard rows may be supplied during vanilla submission. */
    public static <T extends Entity> void register(EntityType<T> type, String texture, Source<T> source) {
        register(type, List.of(new Layer(texture, Mode.DEPTH_WRITING_TRANSLUCENT)),
                (entity, frame, layers) -> source.write(entity, frame, layers.layer(0)));
    }

    /** Registers atomically owned billboard layers, drawn in their material order. */
    public static <T extends Entity> void register(EntityType<T> type, List<Layer> layers,
                                                   LayeredSource<T> source) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        List<Layer> declaredLayers = List.copyOf(layers);
        if (declaredLayers.isEmpty()) throw new IllegalArgumentException("Billboard registration has no layers");
        //? if neoforge && >=26.1 {
        dev.golem.core.flywheel.visual.BillboardEntityVisual.register(
                type, declaredLayers, source);
        //?}
    }

    /**
     * Updates this entity's GPU rows and claims its billboard layer for the current frame.
     * Returns false whenever no healthy visual owns the entity, so callers can render normally.
     */
    public static boolean owns(Entity entity) {
        //? if neoforge && >=26.1 {
        var manager = dev.golem.core.flywheel.visual.VisualizationLifecycle.get(entity.level());
        if (manager == null) return false;
        var visual = manager.entityVisual(entity.getUUID());
        if (visual instanceof dev.golem.core.flywheel.visual.BillboardEntityVisual<?> billboards) {
            return billboards.ownsCurrentFrame();
        }
        return false;
        //?} else {
        /*return false;
        *///?}
    }
}
