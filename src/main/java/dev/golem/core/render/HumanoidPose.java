package dev.golem.core.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;

/**
 * The posed skeleton of the humanoid a layer is drawing on, for renderers that supply their own
 * geometry rather than a vanilla model.
 *
 * <p>1.12's armour models copied angles out of the live {@code ModelBiped} every frame
 * ({@code ModelArmorBase.copyPropertiesFromBiped}). That is unnecessary here: {@link ModelPart} has the same
 * public {@code x/y/z}, {@code xRot/yRot/zRot} and {@code visible} fields and the same
 * {@link ModelPart#translateAndRotate(com.mojang.blaze3d.vertex.PoseStack) translateAndRotate} on every version,
 * so a layer poses against the parent model's parts directly and inherits every vanilla animation
 * (walking, swimming, riding, the zombie arms-raised pose) with nothing to keep in sync.
 *
 * <p>The three flags are the state that does <em>not</em> live on the model. From 26.1 they come from the
 * render state, below it from the entity.
 *
 * <p>Client-only.
 */
public final class HumanoidPose {

    private final HumanoidModel<?> model;

    /** Whether the model has vanilla's crouch pose (it offsets the torso); never while {@link BodyAnimations} pose. */
    public final boolean sneaking;
    /** Whether the entity is a baby, i.e. rendered at the halved scale with an enlarged head. */
    public final boolean baby;
    /** Whether the entity is invisible; layers normally skip drawing entirely when it is. */
    public final boolean invisible;
    /** The player represented by this render state, or null for non-player humanoids. */
    private final Player player;

    public HumanoidPose(HumanoidModel<?> model, boolean sneaking, boolean baby, boolean invisible) {
        this(model, sneaking, baby, invisible, null);
    }

    public HumanoidPose(HumanoidModel<?> model, boolean sneaking, boolean baby, boolean invisible,
            Player player) {
        this.model = model;
        this.sneaking = sneaking;
        this.baby = baby;
        this.invisible = invisible;
        this.player = player;
    }

    /** The model these parts belong to, for the rare caller that needs more than the seven parts. */
    public HumanoidModel<?> model() {
        return model;
    }

    public ModelPart head() {
        return model.head;
    }

    /** The hat/headwear overlay: the part a full helmet usually hides. */
    public ModelPart hat() {
        return model.hat;
    }

    public ModelPart body() {
        return model.body;
    }

    public ModelPart leftArm() {
        return model.leftArm;
    }

    public ModelPart rightArm() {
        return model.rightArm;
    }

    public ModelPart leftLeg() {
        return model.leftLeg;
    }

    public ModelPart rightLeg() {
        return model.rightLeg;
    }

    public Player player() {
        return player;
    }
}
