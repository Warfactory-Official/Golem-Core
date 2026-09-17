package dev.golem.core.mixin;

import dev.golem.core.world.CreeperFuse;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Exposes the creeper's private fuse counters; see {@link CreeperFuse}. */
@Mixin(Creeper.class)
public abstract class MixinCreeperFuse extends LivingEntity implements CreeperFuse {

    @Shadow
    private int swell;

    @Shadow
    private int maxSwell;

    @Shadow
    @Final
    private static EntityDataAccessor<Boolean> DATA_IS_POWERED;

    protected MixinCreeperFuse(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public int golem$swell() {
        return swell;
    }

    @Override
    public void golem$setSwell(int value) {
        swell = value;
    }

    @Override
    public int golem$maxSwell() {
        return maxSwell;
    }

    @Override
    public void golem$setMaxSwell(int value) {
        maxSwell = value;
    }

    @Override
    public void golem$setPowered(boolean powered) {
        entityData.set(DATA_IS_POWERED, powered);
    }
}
