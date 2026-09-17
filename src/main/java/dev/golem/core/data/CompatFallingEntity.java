package dev.golem.core.data;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
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

/** Data/save bridge for custom entities that must retain FallingBlockEntity's vanilla renderer contract. */
public abstract class CompatFallingEntity extends FallingBlockEntity {

    protected CompatFallingEntity(EntityType<? extends FallingBlockEntity> type, Level level) {
        super(type, level);
    }

    /** Replaces the carried block state, which vanilla only assigns in a private constructor. */
    protected final void setCarriedBlockState(net.minecraft.world.level.block.state.BlockState state) {
        ((dev.golem.core.world.FallingBlockState) this).golem$setBlockState(state);
    }

    /** Whether {@code disableDrop()} was called; vanilla keeps the flag private with no getter. */
    protected final boolean cancelDropCompat() {
        return ((dev.golem.core.world.FallingBlockState) this).golem$cancelDrop();
    }

    protected void defineData(SynchedData data) {}
    protected void loadData(DataIn input) {}
    protected void saveData(DataOut output) {}

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

    /** 1.21.1 {@code applyGravity}; the falling block's hand-rolled -0.04 before it. */
    protected final void compatApplyGravity() {
        //? if >=1.21.1 {
        applyGravity();
        //?} else {
        /*if (!isNoGravity()) setDeltaMovement(getDeltaMovement().add(0.0D, -0.04D, 0.0D));
        *///?}
    }

    /** Portal handling is part of an entity's own tick from 1.21.1 on; 1.20.1's falling block had none. */
    protected final void compatHandlePortal() {
        //? if >=1.21.1 {
        handlePortal();
        //?}
    }

    /** 1.21.1 {@code forceTickAfterTeleportToDuplicate}: a falling block ticking once more after a portal duplicate. */
    protected final boolean compatForceTickAfterTeleportToDuplicate() {
        //? if >=1.21.1 {
        return forceTickAfterTeleportToDuplicate;
        //?} else {
        /*return false;
        *///?}
    }
}
