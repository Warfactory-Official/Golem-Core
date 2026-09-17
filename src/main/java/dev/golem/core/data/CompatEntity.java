package dev.golem.core.data;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

//? if >=1.21.1 {
import net.minecraft.network.syncher.SynchedEntityData;
//?}

//? if >=26.1 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
*///?}

/**
 * Entity counterpart to {@link CompatBlockEntity}.
 *
 * <p>This exists for a reason worth stating: entities and block entities persist through <i>different
 * vanilla hooks</i> ({@code addAdditionalSaveData} versus {@code saveAdditional}) but routinely share
 * the same serialisation helpers — a fluid tank's {@code save} method, a status-effect's {@code load}.
 * Converting only block entities to {@link DataIn}/{@link DataOut} would force those shared helpers to
 * carry two signatures, one per caller. Giving entities the same facade keeps every helper single-signature.
 *
 * <p>As with block entities, subclasses override {@link #loadData}/{@link #saveData} and never see the
 * version difference.
 * Subclasses define synchronized data once through {@link #defineData(SynchedData)}; this class bridges
 * vanilla's no-argument and builder-based definition hooks.
 */
public abstract class CompatEntity extends Entity {

    protected CompatEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    /** Neutral hook for entities that are unaffected by explosions. */
    protected boolean shouldIgnoreExplosion() {
        return false;
    }

    //? if >=1.21.1 {
    @Override
    public final boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) {
        return shouldIgnoreExplosion();
    }
    //?}

    //? if <26.1 {
    /*// 26.1 Entity.isInvulnerableToBase: the vanilla invulnerability answer, backported so a subclass can
    // spell it the 26.1 way on every node.
    protected final boolean isInvulnerableToBase(net.minecraft.world.damagesource.DamageSource source) {
        return super.isInvulnerableTo(source);
    }
    *///?}

    /** Reads this entity's persisted state. */
    protected void loadData(DataIn in) {}

    /** Writes this entity's persisted state. */
    protected void saveData(DataOut out) {}

    /** Matches PrimedTnt: portal handling is part of its tick from 1.21.1 onward, not in 1.20.1. */
    protected final void compatHandlePortal() {
        //? if >=1.21.1 {
        handlePortal();
        //?}
    }

    /** Applies the legacy water push hook where the target entity API still exposes it. */
    protected final void compatUpdateInWaterStateAndDoFluidPushing() {
        //? if >=26.1 {
        updateFluidInteraction();
        //?} else {
        /*
        updateInWaterStateAndDoFluidPushing();
        *///?}
    }

    /** Defines synchronized entity data. Called during entity construction. */
    protected void defineData(SynchedData data) {}

    //? if >=1.21.1 {
    @Override
    protected final void defineSynchedData(SynchedEntityData.Builder builder) {
        defineData(builder::define);
    }
    //?} else {
    /*@Override
    protected final void defineSynchedData() {
        defineData(entityData::define);
    }
    *///?}

    //? if >=26.1 {
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        loadData(new ValueDataIn(input));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        saveData(new ValueDataOut(output));
    }
    //?} else {
    /*@Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        loadData(new TagDataIn(tag, level().registryAccess()));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        saveData(new TagDataOut(tag, level().registryAccess()));
    }
    *///?}

    /**
     * Handles damage. Override instead of vanilla's hook, whose name and parameters differ by version:
     * 26.1 splits the server-authoritative path into {@code hurtServer(ServerLevel, …)}, older versions
     * have only {@code hurt(DamageSource, float)}. Return false to ignore the damage entirely.
     *
     * <p>{@link Compat#hurt} is the matching call side; this is the override side, which was missing.
     */
    protected boolean onHurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // Not delegated to super: 26.1 makes Entity.hurtServer ABSTRACT, so there is no inherited
        // behaviour to fall back on and every entity has to state its own answer. Rather than give the
        // two eras different defaults — vanilla's damage handling below 26.1, a compile error above —
        // this models 26.1's contract on every version: unhandled means "takes no damage", which is
        // also what every consumer of this class already does.
        return false;
    }

    //? if >=26.1 {
    @Override
    public final boolean hurtServer(net.minecraft.server.level.ServerLevel level,
                                    net.minecraft.world.damagesource.DamageSource source, float amount) {
        return onHurt(source, amount);
    }
    //?} else {
    /*@Override
    public final boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return onHurt(source, amount);
    }
    *///?}

    /**
     * Handles a player right-clicking this entity. 26.1 passes the hit position; older versions do not.
     * The position is dropped rather than synthesised — no consumer has needed it, and inventing one
     * would be a behavioural difference disguised as compatibility.
     */
    protected net.minecraft.world.InteractionResult onInteract(net.minecraft.world.entity.player.Player player,
                                                               net.minecraft.world.InteractionHand hand) {
        return net.minecraft.world.InteractionResult.PASS;
    }

    //? if >=26.1 {
    @Override
    public final net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,
                                                                net.minecraft.world.InteractionHand hand,
                                                                net.minecraft.world.phys.Vec3 hitPosition) {
        return onInteract(player, hand);
    }
    //?} else {
    /*@Override
    public final net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,
                                                                net.minecraft.world.InteractionHand hand) {
        return onInteract(player, hand);
    }
    *///?}

    /**
     * Client interpolation for this entity, or null for none (vanilla's default for a plain entity). An
     * entity that overrides this owns one {@link dev.golem.core.world.EntityInterpolation} and calls its
     * {@code interpolate()} from its client tick.
     */
    protected dev.golem.core.world.@org.jspecify.annotations.Nullable EntityInterpolation interpolation() {
        return null;
    }

    //? if >=26.1 {
    @Override
    public final net.minecraft.world.entity.@org.jspecify.annotations.Nullable InterpolationHandler getInterpolation() {
        dev.golem.core.world.EntityInterpolation interpolation = interpolation();
        return interpolation == null ? null : interpolation.handler();
    }
    //?} elif >=1.21.1 {
    /*@Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        dev.golem.core.world.EntityInterpolation interpolation = interpolation();
        if (interpolation == null) super.lerpTo(x, y, z, yRot, xRot, steps);
        else interpolation.interpolateTo(new net.minecraft.world.phys.Vec3(x, y, z), yRot, xRot);
    }
    *///?} else {
    /*@Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        dev.golem.core.world.EntityInterpolation interpolation = interpolation();
        if (interpolation == null) super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
        else interpolation.interpolateTo(new net.minecraft.world.phys.Vec3(x, y, z), yRot, xRot);
    }
    *///?}

    /** 26.1 {@code applyEffectsFromBlocks}; {@code checkInsideBlocks} before 1.21.2 (protected there, hence the facade). */
    protected final void compatApplyEffectsFromBlocks() {
        //? if >=26.1 {
        applyEffectsFromBlocks();
        //?} else {
        /*checkInsideBlocks();
        *///?}
    }

    //? if <26.1 {
    /*public net.minecraft.world.entity.item.ItemEntity spawnAtLocation(ServerLevel level, net.minecraft.world.item.ItemStack stack) {
        return super.spawnAtLocation(stack);
    }

    public net.minecraft.world.entity.item.ItemEntity spawnAtLocation(ServerLevel level, net.minecraft.world.item.ItemStack stack, float offset) {
        return super.spawnAtLocation(stack, offset);
    }

    public net.minecraft.world.entity.item.ItemEntity spawnAtLocation(ServerLevel level, net.minecraft.world.level.ItemLike item) {
        return super.spawnAtLocation(item);
    }
    *///?}
}
