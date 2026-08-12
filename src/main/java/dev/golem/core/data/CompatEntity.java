package dev.golem.core.data;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

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
 */
public abstract class CompatEntity extends Entity {

    protected CompatEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    /** Reads this entity's persisted state. */
    protected void loadData(DataIn in) {}

    /** Writes this entity's persisted state. */
    protected void saveData(DataOut out) {}

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
