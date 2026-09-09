package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemStack;

//? if >=26.1 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
*///?}

/**
 * A humanoid render layer written once against 26.1's submit shape, on every version.
 *
 * <p>This is the worn-equipment counterpart to {@link CompatEntityRenderer}, and it hides the same
 * split: from 26.1 a layer is handed a <b>render state</b> and a {@code SubmitNodeCollector} the engine
 * draws from later, while older versions hand it the <b>live entity</b> and a {@code MultiBufferSource}
 * to draw into immediately. Below 26.1 the submissions are replayed through {@link RecordingSubmitSink}
 * so {@link SubmitSink#order} survives rather than being silently dropped — the same reason
 * {@code CompatEntityRenderer} does it, and it matters for exactly the same reason: 26.1 orders
 * translucent geometry by submission, older versions by depth buffer.
 *
 * <p><b>Why this rather than the loaders' armour hooks.</b> NeoForge's
 * {@code IClientItemExtensions#getHumanoidArmorModel}, Fabric's {@code ArmorRenderer} and 26.1's
 * asset-JSON {@code EquipmentLayerRenderer} pipeline are three unrelated designs, and the last of them
 * has no seam for arbitrary geometry at all. A render layer is the one mechanism every node in the
 * matrix expresses the same way, and it is also closer to what 1.12 actually did — {@code getArmorModel}
 * fed the vanilla armour layer.
 *
 * <p><b>Why the supertype is raw.</b> {@code RenderLayer}'s first type parameter <i>is</i> the era
 * split: {@code <S extends EntityRenderState>} from 26.1, {@code <T extends Entity>} below it. Binding
 * it would force every subclass to name an era-specific type and put a version guard in consumer code,
 * which is precisely what this layer exists to prevent. Erasure is identical on both sides, so the
 * bridge methods below override cleanly; the unchecked-ness is contained to this one file and
 * {@link HumanoidLayers}.
 *
 * <p>Client-only.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class CompatHumanoidLayer extends RenderLayer {

    protected CompatHumanoidLayer(RenderLayerParent parent) {
        super(parent);
    }

    /**
     * Draws whatever this layer contributes for the equipment being worn.
     *
     * @param worn  the four armour stacks; never null, empty slots are {@link ItemStack#EMPTY}
     * @param pose  the entity's pose stack, already in body space
     * @param sink  where geometry goes
     * @param light the packed light coordinates for this entity
     * @param parts the parent model's posed skeleton — see {@link HumanoidPose}
     */
    public abstract void submit(Worn worn, PoseStack pose, SubmitSink sink, int light, HumanoidPose parts,
            CameraInfo camera);

    /** The parent humanoid model, or null if this layer was attached to a renderer that has none. */
    protected final HumanoidModel<?> humanoidModel() {
        Object model = getParentModel();
        return model instanceof HumanoidModel<?> humanoid ? humanoid : null;
    }

    //? if >=26.1 {
    /**
     * Bridges vanilla's submit signature. The stacks come straight off the render state — 26.1 snapshots
     * them during extraction, so there is no entity to consult and no attachment API needed.
     */
    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int lightCoords,
            EntityRenderState state, float yRot, float xRot) {

        if (!(state instanceof HumanoidRenderState humanoid)) return;
        HumanoidModel<?> model = humanoidModel();
        if (model == null) return;

        Worn worn = new Worn(humanoid.headEquipment, humanoid.chestEquipment,
                humanoid.legsEquipment, humanoid.feetEquipment);
        if (worn.isEmpty()) return;

        submit(worn, pose, new VanillaSubmitSink(collector), lightCoords,
                new HumanoidPose(model, humanoid.isCrouching, humanoid.isBaby, isInvisible(humanoid),
                        dev.golem.core.client.PlayerRenderHooks.playerForState(humanoid)),
                new CameraInfo(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().position(),
                        net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().yRot(),
                        net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation()));
    }

    /**
     * 26.1 splits "invisible" in two: the entity is invisible, and it is invisible <i>to the viewer</i>
     * (spectators still see teammates). {@code LivingEntityRenderState} keeps only the second as a
     * field; the first is expressed by the state's model being skipped upstream, so this is the flag a
     * layer can actually read.
     */
    private static boolean isInvisible(LivingEntityRenderState state) {
        return state.isInvisibleToPlayer;
    }
    //?} else {
    /*/^*
     * Vanilla's single-pass entry point. Reads the worn stacks off the live entity, runs the neutral
     * submit, then flushes the recorded submissions into the buffer source the engine handed us.
     ^/
    @Override
    public void render(PoseStack pose, MultiBufferSource bufferSource, int packedLight, Entity entity,
            float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
            float netHeadYaw, float headPitch) {

        if (!(entity instanceof LivingEntity living)) return;
        HumanoidModel<?> model = humanoidModel();
        if (model == null) return;

        Worn worn = new Worn(
                living.getItemBySlot(EquipmentSlot.HEAD),
                living.getItemBySlot(EquipmentSlot.CHEST),
                living.getItemBySlot(EquipmentSlot.LEGS),
                living.getItemBySlot(EquipmentSlot.FEET));
        if (worn.isEmpty()) return;

        HumanoidPose parts = new HumanoidPose(model, living.isCrouching(), living.isBaby(),
                living.isInvisible(), living instanceof net.minecraft.world.entity.player.Player player ? player : null);

        RecordingSubmitSink sink = new RecordingSubmitSink();
        net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        submit(worn, pose, sink, packedLight, parts,
                new CameraInfo(camera.getPosition(), camera.getYRot(), camera.rotation()));
        sink.flush(bufferSource, living.level());
    }
    *///?}
}
