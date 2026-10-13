package dev.golem.core.fluid;

//? if neoforge {
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The NeoForge half of a {@link WorldFluids} fluid. NeoForge moves entities by {@code FluidType}, not by the
 * vanilla fluid tags Fabric reads, so the type carries the {@link WorldFluids.Physics} itself: water-like
 * (swim, drown, extinguish, current) or lava-like and no-physics with their own movement, since a fluid type
 * that is neither water-like nor moves its entities itself leaves them frozen on 26.1.
 */
final class WorldFluidType extends net.neoforged.neoforge.fluids.FluidType {

    final WorldFluids.Handle handle;

    WorldFluidType(WorldFluids.Handle handle) {
        super(properties(handle.spec));
        this.handle = handle;
    }

    private static Properties properties(WorldFluids.Spec spec) {
        Properties p = Properties.create()
                .descriptionId(spec.descriptionId())
                .temperature(spec.temperature())
                .density(spec.density())
                .viscosity(spec.viscosity())
                .lightLevel(spec.luminosity())
                .canConvertToSource(false);
        switch (spec.physics()) {
            case WATER -> p = p.canSwim(true).canDrown(true).canExtinguish(true).canPushEntity(true).supportsBoating(true)
                    .canHydrate(true).fallDistanceModifier(0.0F).motionScale(0.014D)
                    //? if >=26.1 {
                    .isWaterLike(true)
                    //?}
                    .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY);
            case LAVA -> p = p.canSwim(false).canDrown(false).canExtinguish(false).canPushEntity(true)
                    .fallDistanceModifier(0.5F).motionScale(0.0023333333333333335D)
                    .pathType(net.minecraft.world.level.pathfinder.PathType.LAVA).adjacentPathType(null)
                    .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                    .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA);
            case NONE -> p = p.canSwim(false).canDrown(false).canExtinguish(false).canPushEntity(false)
                    .fallDistanceModifier(1.0F).motionScale(0.0D).pathType(null).adjacentPathType(null)
                    .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY);
        }
        return p;
    }

    @Override
    public Component getDescription() {
        return Component.translatableWithFallback(handle.spec.descriptionId(), handle.spec.fallbackName());
    }

    @Override
    public Component getDescription(net.neoforged.neoforge.fluids.FluidStack stack) {
        return getDescription();
    }

    //? if >=26.1 {
    @Override
    public boolean move(LivingEntity entity, Vec3 movementVector, double gravity) {
        return FluidMotion.move(handle.spec.physics(), entity, movementVector, gravity, entity.getFluidTypeHeight(this));
    }
    //?} else {
    /*@Override
    public boolean move(net.minecraft.world.level.material.FluidState state, LivingEntity entity, Vec3 movementVector, double gravity) {
        return FluidMotion.move(handle.spec.physics(), entity, movementVector, gravity, entity.getFluidTypeHeight(this));
    }
    *///?}
}
//?} elif forge {
/*import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/^* The Forge half of a {@link WorldFluids} fluid; see the NeoForge spelling of this class. ^/
final class WorldFluidType extends net.minecraftforge.fluids.FluidType {

    final WorldFluids.Handle handle;

    WorldFluidType(WorldFluids.Handle handle) {
        super(properties(handle.spec));
        this.handle = handle;
    }

    private static Properties properties(WorldFluids.Spec spec) {
        Properties p = Properties.create()
                .descriptionId(spec.descriptionId())
                .temperature(spec.temperature())
                .density(spec.density())
                .viscosity(spec.viscosity())
                .lightLevel(spec.luminosity())
                .canConvertToSource(false);
        switch (spec.physics()) {
            case WATER -> p = p.canSwim(true).canDrown(true).canExtinguish(true).canPushEntity(true).supportsBoating(true)
                    .canHydrate(true).fallDistanceModifier(0.0F).motionScale(0.014D)
                    .sound(net.minecraftforge.common.SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(net.minecraftforge.common.SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY);
            case LAVA -> p = p.canSwim(false).canDrown(false).canExtinguish(false).canPushEntity(true)
                    .fallDistanceModifier(0.5F).motionScale(0.0023333333333333335D)
                    .pathType(net.minecraft.world.level.pathfinder.BlockPathTypes.LAVA).adjacentPathType(null)
                    .sound(net.minecraftforge.common.SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                    .sound(net.minecraftforge.common.SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA);
            case NONE -> p = p.canSwim(false).canDrown(false).canExtinguish(false).canPushEntity(false)
                    .fallDistanceModifier(1.0F).motionScale(0.0D).pathType(null).adjacentPathType(null)
                    .sound(net.minecraftforge.common.SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(net.minecraftforge.common.SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY);
        }
        return p;
    }

    @Override
    public Component getDescription() {
        return Component.translatableWithFallback(handle.spec.descriptionId(), handle.spec.fallbackName());
    }

    @Override
    public Component getDescription(net.minecraftforge.fluids.FluidStack stack) {
        return getDescription();
    }

    @Override
    public boolean move(net.minecraft.world.level.material.FluidState state, LivingEntity entity, Vec3 movementVector, double gravity) {
        return FluidMotion.move(handle.spec.physics(), entity, movementVector, gravity, entity.getFluidTypeHeight(this));
    }

    // Forge 1.20.1 asks the type itself for its client extension, on the client only, and from inside the
    // FluidType constructor: before this.handle is assigned, so the extension reads it when it is drawn.
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer) {
        consumer.accept(WorldFluidsClient.extensions(() -> this.handle.spec));
    }
}
*///?}
