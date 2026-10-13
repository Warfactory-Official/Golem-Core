package dev.golem.core.render;

//? if <26.1 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/^*
 * Presents a {@link CompatBER} as a pre-26.1 vanilla {@code BlockEntityRenderer}.
 *
 * <p>Old versions have a single {@code render} call instead of 26.1's extract/submit split, so this
 * runs both back to back: fill the base state, extract, submit into a {@link RecordingSubmitSink},
 * then flush that sink into the {@code MultiBufferSource} the engine handed us.
 *
 * <p>The state and sink are allocated once per renderer instance and reused, as 26.1 reuses a single
 * render state per renderer. That is safe because pre-26.1 block-entity rendering is single-threaded on
 * the render thread.
 *
 * <p>The camera comes from the global render state rather than being passed in, since old versions do
 * not thread it through the renderer contract.
 ^/
public final class LegacyBERAdapter<T extends BlockEntity, S extends CompatRenderState>
        implements BlockEntityRenderer<T> {

    private final CompatBER<T, S> delegate;
    private final S state;
    private final RecordingSubmitSink sink = new RecordingSubmitSink();

    public LegacyBERAdapter(CompatBER<T, S> delegate) {
        this.delegate = delegate;
        this.state = delegate.createRenderState();
    }

    @Override
    public void render(T blockEntity, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        // A removed block entity is never drawn. Vanilla's own lists drop it on removal, but a list that leaks one
        // keeps calling its renderer: Sable's sub-level section rebuild clears a section's "render off-screen" set
        // without telling the level renderer, so a large model that left its sub-level (split, disassembly) kept
        // being drawn where it was, relative to the old sub-level, until relog.
        if (blockEntity.isRemoved()) return;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        // The base fields 26.1's BlockEntityRenderState.extractBase would have filled in.
        state.blockPos = blockEntity.getBlockPos();
        state.lightCoords = packedLight;

        delegate.extract(blockEntity, state, partialTicks, cameraPos);
        delegate.submit(state, poseStack, sink,
                new CameraInfo(cameraPos, camera.getYRot(), camera.rotation()));
        sink.flush(bufferSource, blockEntity.getLevel());
    }

    @Override
    public int getViewDistance() {
        return delegate.getViewDistance();
    }

    @Override
    public boolean shouldRender(T blockEntity, Vec3 cameraPosition) {
        return delegate.shouldRender(blockEntity, cameraPosition);
    }

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        // Vanilla passes the block entity here; 26.1's hook takes none, so the argument is dropped.
        return delegate.shouldRenderOffScreen();
    }

    //? if neoforge {
    /^*
     * NeoForge culls each block-entity renderer against this box:
     * {@code frustum.isVisible(renderer.getRenderBoundingBox(be))} in {@code ClientHooks}, and the
     * default is the unit cube at the block's position. A machine whose model spans several blocks
     * therefore vanishes as soon as its origin block leaves the frustum (big machines pop out at the
     * screen edges on NeoForge; Fabric has no such hook).
     *
     * <p>A renderer that declares {@code shouldRenderOffScreen()} draws outside its own block, so it gets
     * {@code AABB.INFINITE}, NeoForge's "never cull me" value. Any other renderer keeps the default.
     ^/
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(T blockEntity) {
        return delegate.shouldRenderOffScreen()
                ? net.minecraft.world.phys.AABB.INFINITE
                : new net.minecraft.world.phys.AABB(blockEntity.getBlockPos());
    }
    //?}
}
*///?}
