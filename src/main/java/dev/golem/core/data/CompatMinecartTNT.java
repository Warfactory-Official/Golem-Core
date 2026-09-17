package dev.golem.core.data;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;

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
 * Facade for custom {@code MinecartTNT} subclasses.
 *
 * <p>Same contract as {@link CompatEntity}: subclasses define synchronized data through
 * {@link #defineData(SynchedData)} and persist through {@link #loadData(DataIn)}/{@link #saveData(DataOut)}.
 * Every other hook keeps its 26.1 spelling; below 26.1 this class declares that spelling and forwards
 * the older vanilla hook into it, so a subclass written against 26.1 compiles unchanged on every node.
 */
public abstract class CompatMinecartTNT extends net.minecraft.world.entity.vehicle.minecart.MinecartTNT {

    protected CompatMinecartTNT(EntityType<? extends MinecartTNT> type, Level level) {
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
    /*// 26.1 spelling of the server damage path. Below 26.1 vanilla only has {@code hurt}, which runs on
    // both sides; the client half is kept vanilla so only the server call reaches the 26.1-shaped hook.
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return super.hurt(source, amount);
    }

    @Override
    public final boolean hurt(DamageSource source, float amount) {
        if (!(level() instanceof ServerLevel server)) return super.hurt(source, amount);
        return hurtServer(server, source, amount);
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

    //? if <26.1 {
    /*public void activateMinecart(ServerLevel level, int x, int y, int z, boolean powered) {
        super.activateMinecart(x, y, z, powered);
    }

    @Override
    public final void activateMinecart(int x, int y, int z, boolean powered) {
        activateMinecart((ServerLevel) level(), x, y, z, powered);
    }

    public void destroy(ServerLevel level, DamageSource source) {
        super.destroy(source);
    }

    @Override
    public final void destroy(DamageSource source) {
        destroy((ServerLevel) level(), source);
    }
    *///?}
}
