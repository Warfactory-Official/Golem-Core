package dev.golem.core.gemrender;

import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import dev.golem.core.GolemCore;
import dev.golem.core.gemrender.mixin.GemRenderMixinPlugin;
import dev.golem.core.platform.Platform;
import dev.golem.core.render.CompatRenderState;
import dev.golem.core.render.Instancing;
import dev.golem.core.render.gem.GemBER;
import java.util.function.Supplier;
//? if neoforge && >=26.1 {
import net.caffeinemc.mods.sodium.api.blockentity.BlockEntityRenderHandler;
//?}
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
    /** GemRender 0.1.14, whose faults Core works around here and in {@link GemModels}; later releases fix them. */
    static final boolean UNFIXED = GemRenderMixinPlugin.isPatched(Platform.modVersion("gemrender"));

    private GemRender() {}

    public static <T extends BlockEntity, S extends CompatRenderState> void register(
            BlockEntityType<T> type, Supplier<? extends GemBER<T, S>> renderer) {
        install();
        //? if neoforge && >=26.1 {
        if (UNFIXED && Platform.isModLoaded("sodium")) SodiumBlockEntities.add(type);
        //?}
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

    /**
     * GemRender 0.1.14 faults: its 1.21.1 NeoForge light grid uploads under a leftover unpack state and reads past its
     * buffer, and its 26.1 Flywheel stores NeoForge's fog but never uploads it.
     */
    private static void install() {
        if (installed) return;
        installed = true;
        GolemCore.LOG.info("Binding gem visuals to GemRender{}", UNFIXED ? " (with Core's 0.1.14 workarounds)" : "");
        if (!UNFIXED) return;
        //? if neoforge && <26.1 {
        /*net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) -> {
                    com.mojang.blaze3d.platform.GlStateManager._pixelStore(org.lwjgl.opengl.GL11.GL_UNPACK_ROW_LENGTH, 0);
                    com.mojang.blaze3d.platform.GlStateManager._pixelStore(org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_PIXELS, 0);
                    com.mojang.blaze3d.platform.GlStateManager._pixelStore(org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_ROWS, 0);
                    com.mojang.blaze3d.platform.GlStateManager._pixelStore(org.lwjgl.opengl.GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
                    com.mojang.blaze3d.platform.GlStateManager._pixelStore(org.lwjgl.opengl.GL12.GL_UNPACK_SKIP_IMAGES, 0);
                });
        *///?}
        //? if neoforge && >=26.1 {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                (net.neoforged.neoforge.client.event.ViewportEvent.RenderFog event) ->
                        dev.engine_room.flywheel.backend.engine.uniform.FogUniforms.update());
        //?}
    }

    //? if neoforge && >=26.1 {
    /**
     * Upstream Flywheel's Sodium predicate, stubbed in GemRender 0.1.14's 26.1 fork: without it Sodium never hands a
     * loaded block entity to Flywheel.
     */
    private static final class SodiumBlockEntities {

        private SodiumBlockEntities() {}

        static <T extends BlockEntity> void add(BlockEntityType<T> type) {
            BlockEntityRenderHandler.instance().addRenderPredicate(type,
                    (getter, pos, blockEntity) -> !VisualizationHelper.tryAddBlockEntity(blockEntity));
        }
    }
    //?}

    public static boolean isVisualizing(LevelAccessor level) {
        return VisualizationManager.supportsVisualization(level);
    }

    public static boolean owns(BlockPos pos) {
        return GemVisual.owns(pos);
    }

    /** The selected backend's path ({@code instancing}, {@code indirect}, {@code off}). */
    static String backendName() {
        Backend backend = BackendManager.currentBackend();
        return backend == null ? "starting" : Backend.REGISTRY.getIdOrThrow(backend).getPath();
    }

    public static Instancing.Report report() {
        int[] census = GemVisual.census();
        return new Instancing.Report(backendName(), census[0], census[1], census[2], census[3],
                -1, GemModels.modelCount(), census[4], census[5], census[6], census[7]);
    }
}
