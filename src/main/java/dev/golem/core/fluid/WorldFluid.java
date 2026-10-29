package dev.golem.core.fluid;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * The vanilla half of a {@link WorldFluids} fluid: what the renderer, buckets, entities and other mods see. A
 * {@code FlowingFluid}, so a vanilla bucket can place it and vanilla's heights, shapes and currents apply; the
 * flowing itself is not vanilla's but 1.7's, run by the {@link FluidBlock} on block ticks. A fluid tick that
 * anything else schedules on it is handed to that block as well.
 *
 * <p>The source is a full block; the flowing fluid's {@code level} (1..8) is the fraction of a block it holds,
 * {@code remaining quanta * 8 / quanta}, so a quanta-4 fluid steps 8, 6, 4, 2 and renders at those heights.
 */
public abstract class WorldFluid extends FlowingFluid {

    final WorldFluids.Handle handle;

    WorldFluid(WorldFluids.Handle handle) {
        this.handle = handle;
    }

    public WorldFluids.Handle handle() {
        return handle;
    }

    @Override
    public Fluid getFlowing() {
        return handle.flowing();
    }

    @Override
    public Fluid getSource() {
        return handle.source();
    }

    @Override
    public boolean isSame(Fluid other) {
        return other == handle.source() || other == handle.flowing();
    }

    @Override
    public Item getBucket() {
        Item bucket = handle.bucketItem();
        return bucket == null ? Items.AIR : bucket;
    }

    /**
     * 1.7 {@code BlockFluidClassic} never makes sources.
     */
    //? if >=26.1 {
    @Override
    protected boolean canConvertToSource(net.minecraft.server.level.ServerLevel level) {
        return false;
    }
    //?} else {
    /*@Override
    protected boolean canConvertToSource(Level level) {
        return false;
    }
    *///?}

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        Block.dropResources(state, level, pos, state.hasBlockEntity() ? level.getBlockEntity(pos) : null);
    }

    // Vanilla's spreading never runs (the tick below replaces it); these answer anyone who asks anyway.
    @Override
    protected int getSlopeFindDistance(LevelReader level) {
        return 4;
    }

    @Override
    protected int getDropOff(LevelReader level) {
        return Math.max(1, 8 / handle.spec.quanta());
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return handle.spec.tickDelay();
    }

    /**
     * Nothing washes this fluid away: in 1.7 another liquid could not flow into a fluid block's space either.
     */
    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other,
                                        Direction direction) {
        return false;
    }

    @Override
    protected float getExplosionResistance() {
        return 100.0F;
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
        FluidBlock block = handle.block();
        int quanta = handle.spec.quanta();
        int remaining = state.isSource() ? quanta
                : Math.max(1, Math.min(quanta - 1, Math.round(state.getAmount() * quanta / 8.0F)));
        return block.withQuanta(remaining);
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(
                handle.spec.physics() == WorldFluids.Physics.LAVA ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL);
    }

    //? if >=26.1 {
    @Override
    protected void entityInside(Level level, BlockPos pos, net.minecraft.world.entity.Entity entity,
                                net.minecraft.world.entity.InsideBlockEffectApplier effects) {
        if (handle.spec.physics() == WorldFluids.Physics.WATER) {
            effects.apply(net.minecraft.world.entity.InsideBlockEffectType.EXTINGUISH);
        } else {
            super.entityInside(level, pos, entity, effects);
        }
    }

    @Override
    public void tick(net.minecraft.server.level.ServerLevel level, BlockPos pos, BlockState blockState,
                     FluidState fluidState) {
        if (blockState.getBlock() instanceof FluidBlock block)
            block.flowTick(level, pos, blockState, level.getRandom());
    }
    //?} else {
    /*@Override
    public void tick(Level level, BlockPos pos, FluidState fluidState) {
        BlockState blockState = level.getBlockState(pos);
        if (level instanceof net.minecraft.server.level.ServerLevel server && blockState.getBlock() instanceof FluidBlock block) {
            block.flowTick(server, pos, blockState, level.getRandom());
        }
    }
    *///?}

    //? if neoforge {
    @Override
    public net.neoforged.neoforge.fluids.FluidType getFluidType() {
        return handle.type.get();
    }
    //?} elif forge {
    /*@Override
    public net.minecraftforge.fluids.FluidType getFluidType() {
        return handle.type.get();
    }
    *///?}

    /**
     * The full block.
     */
    static final class Source extends WorldFluid {

        Source(WorldFluids.Handle handle) {
            super(handle);
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }
    }

    /**
     * A partial block: {@code level} 1..7 is how much of a block it holds.
     */
    static final class Flowing extends WorldFluid {

        Flowing(WorldFluids.Handle handle) {
            super(handle);
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }
    }
}
