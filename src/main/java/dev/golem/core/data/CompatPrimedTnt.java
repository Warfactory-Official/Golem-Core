package dev.golem.core.data;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.item.PrimedTnt;

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
 * Facade for custom {@code PrimedTnt} subclasses; 26.1 makes its damage hook final, so none is bridged.
 *
 * <p>Same contract as {@link CompatEntity}: subclasses define synchronized data through
 * {@link #defineData(SynchedData)} and persist through {@link #loadData(DataIn)}/{@link #saveData(DataOut)}.
 * Every other hook keeps its 26.1 spelling; below 26.1 this class declares that spelling and forwards
 * the older vanilla hook into it, so a subclass written against 26.1 compiles unchanged on every node.
 */
public abstract class CompatPrimedTnt extends net.minecraft.world.entity.item.PrimedTnt {

    protected CompatPrimedTnt(EntityType<? extends PrimedTnt> type, Level level) {
        super(type, level);
    }

    /** Defines synchronized entity data. Called during entity construction, after the vanilla base state. */
    protected void defineData(SynchedData data) {}

    /** Reads this entity's persisted state, after the vanilla base state. */
    protected void loadData(DataIn in) {}

    /** Writes this entity's persisted state, after the vanilla base state. */
    protected void saveData(DataOut out) {}

    //? if >=1.21.1 {
    @Override
    protected final void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        defineData(builder::define);
    }
    //?} else {
    /*@Override
    protected final void defineSynchedData() {
        super.defineSynchedData();
        defineData(entityData::define);
    }
    *///?}

    //? if >=26.1 {
    @Override
    protected final void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        loadData(new ValueDataIn(input));
    }

    @Override
    protected final void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        saveData(new ValueDataOut(output));
    }
    //?} else {
    /*@Override
    public final void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadData(new TagDataIn(tag, level().registryAccess()));
    }

    @Override
    public final void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        saveData(new TagDataOut(tag, level().registryAccess()));
    }
    *///?}

    //? if <26.1 {
    /*public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return super.causeFallDamage((float) fallDistance, damageModifier, damageSource);
    }

    @Override
    public final boolean causeFallDamage(float fallDistance, float damageModifier, DamageSource damageSource) {
        return causeFallDamage((double) fallDistance, damageModifier, damageSource);
    }
    *///?}

    //? if <26.1 {
    /*public void lerpMotion(net.minecraft.world.phys.Vec3 movement) {
        super.lerpMotion(movement.x, movement.y, movement.z);
    }

    @Override
    public final void lerpMotion(double x, double y, double z) {
        lerpMotion(new net.minecraft.world.phys.Vec3(x, y, z));
    }
    *///?}

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

    //? if <1.21.1 {
    /*protected net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        return super.getDimensions(pose);
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return getDefaultDimensions(pose);
    }
    *///?}

    //? if <26.1 {
    /*public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 location) {
        return super.interact(player, hand);
    }

    @Override
    public final net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {
        return interact(player, hand, position());
    }
    *///?}

    /** The private vanilla igniter (an {@code EntityReference} on 26.1, a direct field before), through Golem's TNT mixin. */
    protected final void setOwnerCompat(net.minecraft.world.entity.@org.jspecify.annotations.Nullable LivingEntity owner) {
        ((dev.golem.core.world.TntOwner) this).golem$setOwner(owner);
    }

    /**
     * Replaces vanilla's detonation: return true to skip it. Vanilla's {@code explode} is private on Fabric
     * and only widened by the Forge-like loaders, so Golem's TNT mixin asks this at its head on every node.
     */
    protected boolean explodeCompat() {
        return false;
    }

    /** Overrides the default the TNT mixin gives every primed TNT (same erased signature, no compile-time interface). */
    public final boolean golem$explode() {
        return explodeCompat();
    }

    //? if <1.21.1 {
    /*// 1.20.3 gave primed TNT a carried block state; 1.20.1 has none, so the facade carries one (not persisted:
    // a custom TNT lives 80 ticks and 1.7 never saved its bomb block either).
    private net.minecraft.world.level.block.state.BlockState compatBlockState =
            net.minecraft.world.level.block.Blocks.TNT.defaultBlockState();

    public net.minecraft.world.level.block.state.BlockState getBlockState() {
        return compatBlockState;
    }

    public void setBlockState(net.minecraft.world.level.block.state.BlockState state) {
        compatBlockState = state;
    }
    *///?}
}
