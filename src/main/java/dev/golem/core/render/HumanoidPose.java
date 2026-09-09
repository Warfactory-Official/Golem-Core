package dev.golem.core.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;

/**
 * The posed skeleton of the humanoid a layer is drawing on, for renderers that supply their own
 * geometry rather than a vanilla model.
 *
 * <p>1.12's armour models solved this by <i>copying</i> angles out of the live {@code ModelBiped}
 * every frame ({@code ModelArmorBase.copyPropertiesFromBiped}). That copy is unnecessary here:
 * {@link ModelPart} is the same class on every version in the matrix, with the same public
 * {@code x/y/z}, {@code xRot/yRot/zRot} and {@code visible} fields and the same
 * {@link ModelPart#translateAndRotate(com.mojang.blaze3d.vertex.PoseStack) translateAndRotate}, so a
 * layer can pose against the parent model's parts directly and inherit every animation vanilla applies
 * — walking, swimming, riding, the zombie arms-raised pose — with nothing to keep in sync.
 *
 * <p>The three flags are the pieces of that state which do <em>not</em> live on the model, and which
 * the two eras read from different places: above 26.1 from the render state, below it from the entity.
 *
 * <p>Client-only.
 */
public final class HumanoidPose {

    private final HumanoidModel<?> model;

    /** Whether the entity is crouching. Armour bodies need this: vanilla's crouch offsets the torso. */
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

    /** The hat/headwear overlay — the part a full helmet usually hides. */
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
