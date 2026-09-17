package dev.golem.core.data;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.monster.Creeper;

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
 * Facade for custom {@code Creeper} subclasses.
 *
 * <p>Same contract as {@link CompatEntity}: subclasses define synchronized data through
 * {@link #defineData(SynchedData)} and persist through {@link #loadData(DataIn)}/{@link #saveData(DataOut)}.
 * Every other hook keeps its 26.1 spelling; below 26.1 this class declares that spelling and forwards
 * the older vanilla hook into it, so a subclass written against 26.1 compiles unchanged on every node.
 */
public abstract class CompatCreeper extends net.minecraft.world.entity.monster.Creeper {

    protected CompatCreeper(EntityType<? extends Creeper> type, Level level) {
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
    /*public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return super.isInvulnerableTo(source);
    }

    @Override
    public final boolean isInvulnerableTo(DamageSource source) {
        if (!(level() instanceof ServerLevel server)) return super.isInvulnerableTo(source);
        return isInvulnerableTo(server, source);
    }

    // 26.1 Entity.isInvulnerableToBase: the vanilla answer without the subclass's override.
    protected final boolean isInvulnerableToBase(DamageSource source) {
        return super.isInvulnerableTo(source);
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
    /*protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep();
    }

    @Override
    protected final void customServerAiStep() {
        customServerAiStep((ServerLevel) level());
    }

    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        return super.doHurtTarget(target);
    }

    @Override
    public final boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        return doHurtTarget((ServerLevel) level(), target);
    }

    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment();
    }

    @Override
    protected final void dropEquipment() {
        dropEquipment((ServerLevel) level());
    }
    *///?}

    //? if >=1.21.1 && <26.1 {
    /*protected void triggerOnDeathMobEffects(ServerLevel level, RemovalReason reason) {
        super.triggerOnDeathMobEffects(reason);
    }

    @Override
    protected final void triggerOnDeathMobEffects(RemovalReason reason) {
        // Vanilla calls this on both sides here; 26.1 only ever hands a subclass the server level.
        if (level() instanceof ServerLevel server) triggerOnDeathMobEffects(server, reason);
        else super.triggerOnDeathMobEffects(reason);
    }
    *///?}

    //? if <1.21.1 {
    /*// 1.20.1 has no on-death mob-effect hook (it arrived with the 1.21 oozing/infested effects), so the
    // 26.1 spelling is a no-op there; subclasses that call it lose nothing that 1.20.1 vanilla had.
    protected void triggerOnDeathMobEffects(ServerLevel level, RemovalReason reason) {}

    private int compatLootingLevel;

    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(source, compatLootingLevel, killedByPlayer);
    }

    @Override
    protected final void dropCustomDeathLoot(DamageSource source, int lootingLevel, boolean killedByPlayer) {
        compatLootingLevel = lootingLevel;
        dropCustomDeathLoot((ServerLevel) level(), source, killedByPlayer);
    }

    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.MobSpawnType reason,
            net.minecraft.world.entity.SpawnGroupData groupData) {
        return super.finalizeSpawn(level, difficulty, reason, groupData, null);
    }

    @Override
    public final net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.MobSpawnType reason,
            net.minecraft.world.entity.SpawnGroupData groupData, CompoundTag tag) {
        return finalizeSpawn(level, difficulty, reason, groupData);
    }
    *///?}

    /** The private vanilla fuse counter, through Golem's creeper mixin. */
    protected final int swell() {
        return ((dev.golem.core.world.CreeperFuse) this).golem$swell();
    }

    protected final void setSwell(int swell) {
        ((dev.golem.core.world.CreeperFuse) this).golem$setSwell(swell);
    }

    /** The private vanilla fuse length (30 by default), through Golem's creeper mixin. */
    protected final int maxSwell() {
        return ((dev.golem.core.world.CreeperFuse) this).golem$maxSwell();
    }

    protected final void setMaxSwell(int maxSwell) {
        ((dev.golem.core.world.CreeperFuse) this).golem$setMaxSwell(maxSwell);
    }

    //? if <1.21.1 {
    /*// 1.21 moved the creature class (Smite / Bane of Arthropods) into entity-type tags; 1.20.1 still asks
    // this hook, so it is answered from the same tags and one data JSON serves every node.
    @Override
    public net.minecraft.world.entity.MobType getMobType() {
        return dev.golem.core.world.CreatureTypes.mobType(this, super.getMobType());
    }
    *///?}
}
