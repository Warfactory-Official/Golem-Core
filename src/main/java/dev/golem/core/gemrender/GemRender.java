package dev.golem.core.gemrender;

import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.golem.core.GolemCore;
import dev.golem.core.platform.Platform;
import dev.golem.core.render.CompatRenderState;
import dev.golem.core.render.Instancing;
import dev.golem.core.render.gem.GemBER;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Core's only door into GemRender and the Flywheel it carries. Load it only after {@link Instancing#isAvailable}
 * said yes: linking this class resolves Flywheel types, which a client without GemRender does not have.
 */
public final class GemRender {

    private static boolean installed;
    /** GemRender 0.1.21 and older's own 26.1 Flywheel: blind to Iris, and lights back faces as 26.1 vanilla does. */
    static final boolean FORK = String.valueOf(Platform.modVersion("flywheel")).endsWith("-gemrender");

    private GemRender() {}

    public static <T extends BlockEntity, S extends CompatRenderState> void register(
            BlockEntityType<T> type, Supplier<? extends GemBER<T, S>> renderer) {
        if (!installed) {
            installed = true;
            GolemCore.LOG.info("Binding gem visuals to GemRender");
        }
        VisualizerRegistry.setVisualizer(type, new BlockEntityVisualizer<T>() {
            @Override
            public BlockEntityVisual<? super T> createVisual(VisualizationContext ctx, T blockEntity,
                                                             float partialTick) {
                return new GemVisual<>(ctx, blockEntity, partialTick, renderer.get());
            }

            // Never: submitUndeclared geometry has no instance and must keep being submitted. GemBER asks
            // Visuals.owns for the declared tree instead.
            @Override
            public boolean skipVanillaRender(T blockEntity) {
                return false;
            }
        });
    }

    public static boolean isVisualizing(LevelAccessor level) {
        return VisualizationManager.supportsVisualization(level);
    }

    public static boolean owns(BlockPos pos) {
        return GemVisual.owns(pos);
    }

    /**
     * Whether instances reach the picture under a shader pack. Flywheel's own backends stand down under one, so a
     * backend still on is a compat mod's (Colorwheel, irisflw) drawing through the pack.
     */
    static boolean instancesUnderPack() {
        return !FORK && BackendManager.isBackendOn();
    }

    /** The selected backend's path ({@code instancing}, {@code indirect}, {@code off}), namespaced unless Flywheel's. */
    static String backendName() {
        Backend backend = BackendManager.currentBackend();
        if (backend == null) return "starting";
        var id = Backend.REGISTRY.getIdOrThrow(backend);
        return id.getNamespace().equals("flywheel") ? id.getPath() : id.toString();
    }

    public static Instancing.Report report() {
        int[] census = GemVisual.census();
        return new Instancing.Report(backendName(), census[0], census[1], census[2], census[3],
                -1, GemModels.modelCount(), census[4], census[5], census[6], census[7]);
    }
}
