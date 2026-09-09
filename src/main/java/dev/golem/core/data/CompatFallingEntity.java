package dev.golem.core.data;

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
}
