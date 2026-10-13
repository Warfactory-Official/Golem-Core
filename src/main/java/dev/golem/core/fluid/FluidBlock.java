package dev.golem.core.fluid;

import dev.golem.core.world.CompatBlock;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The block of a {@link WorldFluids} fluid: Forge 1.7.10's {@code BlockFluidBase} with its two flow models,
 * {@code BlockFluidClassic} and {@code BlockFluidFinite}, on a modern block. Subclass it for a fluid's own
 * behaviour, overriding the same methods a 1.7 fluid block overrode:
 *
 * <ul>
 *   <li>{@link #canDisplace} / {@link #displaceIfPossible}: what the fluid may flow into (and destroy);</li>
 *   <li>{@link #afterFlow}: the part of a 1.7 {@code updateTick} override after {@code super.updateTick};</li>
 *   <li>{@code onEntityInside}, {@code onNeighborChanged} (call super: it schedules the flow), {@code animateTick},
 *       {@code randomTick} as on any {@code CompatBlock}.</li>
 * </ul>
 *
 * <p>State: {@code level} 0 is a full block (a classic source, a full finite block, and the default state, so
 * {@code defaultBlockState()} places a source); {@code level n} holds {@code quanta - n} quanta. The 1.7
 * metadata is {@code level} for a classic fluid and {@code quanta - 1 - level} for a finite one.
 *
 * <p>The block has no shape, so no tool can target or break it; it has no collision and drops nothing.
 * {@link #properties} gives the rest of 1.7's block properties.
 */
public class FluidBlock extends CompatBlock implements BucketPickup {

    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;

    private final WorldFluids.Handle fluid;
    private final int quanta;
    /** 1.7 {@code densityDir}: -1 flows down, +1 (a negative density) flows up. */
    private final int densityDir;
    private final int density;
    private final int tickRate;
    private final FluidState[] states = new FluidState[16];

    public FluidBlock(Properties properties, WorldFluids.Handle fluid) {
        super(properties);
        this.fluid = fluid;
        this.quanta = fluid.spec().quanta();
        this.density = fluid.spec().density();
        this.densityDir = density > 0 ? -1 : 1;
        this.tickRate = fluid.spec().tickDelay();
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
    }

    /**
     * 1.7's {@code BlockFluidBase} properties: no collision, hardness 100, no drops, destroyed by pistons, no
     * sound, a liquid, random ticks ({@code setTickRandomly(true)} for every Forge fluid, which is also what wakes
     * a pool worldgen placed); and the light of a 1.7 fluid, {@code (int) (meta / quanta * luminosity)}
     * ({@code BlockFluidBase.getLightValue}, Forge 1.7.10). Odd but load-bearing: a classic fluid's
     * source (metadata 0) gives no light and its thin edges the most, while a full finite block (metadata
     * {@code quanta - 1}) is brightest. Replaceability is the caller's: it was the fluid material's in 1.7.
     */
    public static BlockBehaviour.Properties properties(BlockBehaviour.Properties p, WorldFluids.Handle fluid) {
        //? if >=26.1 {
        p = p.noCollision();
        //?} else {
        /*p = p.noCollission();
        *///?}
        p = p.strength(100.0F).noOcclusion().noLootTable().liquid().pushReaction(PushReaction.DESTROY).sound(SoundType.EMPTY)
                .randomTicks();
        WorldFluids.Spec spec = fluid.spec();
        if (spec.luminosity() > 0) p = p.lightLevel(state -> light(spec, state.getValue(LEVEL)));
        return p;
    }

    static int light(WorldFluids.Spec spec, int level) {
        int q = spec.quanta();
        int remaining = q - Math.min(level, q - 1);
        int meta = spec.isFinite() ? remaining - 1 : q - remaining;
        return (int) (meta / (float) q * spec.luminosity());
    }

    public WorldFluids.Handle fluid() {
        return fluid;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    // State and the fluid it stands for

    /** Quanta held by {@code state}, 1..quanta. */
    public int quantaOf(BlockState state) {
        return quanta - Math.min(state.getValue(LEVEL), quanta - 1);
    }

    /** This block holding {@code remaining} quanta (clamped to 1..quanta). */
    public BlockState withQuanta(int remaining) {
        int r = Math.max(1, Math.min(quanta, remaining));
        return defaultBlockState().setValue(LEVEL, quanta - r);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        int level = state.getValue(LEVEL);
        FluidState cached = states[level];
        if (cached == null) {
            int remaining = quantaOf(state);
            cached = remaining >= quanta ? fluid.source().defaultFluidState()
                    : fluid.flowing().getFlowing(Math.max(1, Math.min(7, Math.round(remaining * 8.0F / quanta))), false);
            states[level] = cached;
        }
        return cached;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    // Ticks (1.7 BlockFluidBase)

    /** 1.7 {@code onBlockAdded}: a fluid block arriving schedules its first flow. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(this)) level.scheduleTick(pos, this, tickRate);
    }

    /** 1.7 {@code onNeighborBlockChange}: any neighbour change schedules a flow. Subclasses call super. */
    @Override
    protected void onNeighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock, boolean movedByPiston) {
        if (!level.isClientSide()) level.scheduleTick(pos, this, tickRate);
    }

    @Override
    public final void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        flowTick(level, pos, state, random);
    }

    /** 1.7 {@code Block.randomTick}: a randomly ticking fluid runs its whole update. */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        flowTick(level, pos, state, random);
    }

    /** One 1.7 {@code updateTick}: the flow, then {@link #afterFlow}. */
    public final void flowTick(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (!state.is(this)) return;
        if (fluid.spec().isFinite()) flowFinite(level, pos, state);
        else flowClassic(level, pos, state);
        afterFlow(level, pos, random);
    }

    /** What a 1.7 fluid's {@code updateTick} override did after {@code super.updateTick}. The block may be gone. */
    protected void afterFlow(ServerLevel level, BlockPos pos, RandomSource random) {}

    protected final void scheduleFlow(Level level, BlockPos pos) {
        level.scheduleTick(pos, this, tickRate);
    }

    // Displacement (1.7 BlockFluidBase)

    /**
     * 1.7 {@code displacements}: doors, signs and sugar cane hold a fluid back, and the fluid never displaces
     * itself. {@code null} for a block the rest of {@link #canDisplace} decides.
     */
    protected @Nullable Boolean displacement(BlockState state) {
        if (state.is(this)) return Boolean.FALSE;
        if (state.getBlock() instanceof DoorBlock || state.is(BlockTags.SIGNS) || state.is(Blocks.SUGAR_CANE)) return Boolean.FALSE;
        return null;
    }

    /** 1.7 {@code Material.blocksMovement() || material == Material.portal}. */
    protected static boolean blocksFluid(BlockState state) {
        return state.blocksMotion() || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
    }

    /** 1.7 {@code getDensity}: a fluid block's density; anything else counts as infinitely displaceable. */
    protected static int densityAt(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof FluidBlock other ? other.density : Integer.MAX_VALUE;
    }

    /** Whether {@code pos} holds a liquid (1.7 {@code Material.isLiquid()}): vanilla water/lava or a world fluid. */
    public static boolean isLiquid(BlockState state) {
        return state.liquid();
    }

    /** 1.7 {@code BlockFluidBase.canDisplace}. */
    protected boolean canDisplace(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        Boolean fixed = displacement(state);
        if (fixed != null) return fixed;
        if (blocksFluid(state)) return false;
        int other = densityAt(level, pos);
        return other == Integer.MAX_VALUE || density > other;
    }

    /** 1.7 {@code BlockFluidBase.displaceIfPossible}: as {@link #canDisplace}, dropping what it washes away. */
    protected boolean displaceIfPossible(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        Boolean fixed = displacement(state);
        if (fixed != null) {
            if (fixed) Block.dropResources(state, level, pos, state.hasBlockEntity() ? level.getBlockEntity(pos) : null);
            return fixed;
        }
        if (blocksFluid(state)) return false;
        int other = densityAt(level, pos);
        if (other == Integer.MAX_VALUE) {
            Block.dropResources(state, level, pos, state.hasBlockEntity() ? level.getBlockEntity(pos) : null);
            return true;
        }
        return density > other;
    }

    // BlockFluidClassic

    /** 1.7 {@code isSourceBlock}. */
    public boolean isSourceBlock(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(this) && state.getValue(LEVEL) == 0;
    }

    /** 1.7 {@code getQuantaValue} (classic): air 0, another block -1, else the quanta it holds. */
    public int getQuantaValue(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return 0;
        if (!state.is(this)) return -1;
        return quantaOf(state);
    }

    private int getLargerQuanta(BlockGetter level, BlockPos pos, int compare) {
        int quantaRemaining = getQuantaValue(level, pos);
        if (quantaRemaining <= 0) return compare;
        return Math.max(quantaRemaining, compare);
    }

    private BlockPos vertical(BlockPos pos) {
        return densityDir < 0 ? pos.below() : pos.above();
    }

    private BlockPos counterVertical(BlockPos pos) {
        return densityDir < 0 ? pos.above() : pos.below();
    }

    /** 1.7 {@code BlockFluidClassic.canFlowInto}. */
    protected boolean canFlowInto(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (state.is(this)) return true;
        Boolean fixed = displacement(state);
        if (fixed != null) return fixed;
        FluidState fluidState = state.getFluidState();
        if (blocksFluid(state) || fluidState.is(FluidTags.WATER) || fluidState.is(FluidTags.LAVA)) return false;
        int other = densityAt(level, pos);
        return other == Integer.MAX_VALUE || density > other;
    }

    /** 1.7 {@code isFlowingVertically}. */
    public boolean isFlowingVertically(BlockGetter level, BlockPos pos) {
        return level.getBlockState(vertical(pos)).is(this)
                || (level.getBlockState(pos).is(this) && canFlowInto(level, vertical(pos)));
    }

    private void flowClassic(ServerLevel level, BlockPos pos, BlockState state) {
        int quantaRemaining = quantaOf(state);
        int expQuanta;

        // check adjacent block levels if non-source
        if (quantaRemaining < quanta) {
            BlockPos above = counterVertical(pos);
            if (level.getBlockState(above).is(this)
                    || level.getBlockState(above.west()).is(this) || level.getBlockState(above.east()).is(this)
                    || level.getBlockState(above.north()).is(this) || level.getBlockState(above.south()).is(this)) {
                expQuanta = quanta - 1;
            } else {
                int maxQuanta = -100;
                maxQuanta = getLargerQuanta(level, pos.west(), maxQuanta);
                maxQuanta = getLargerQuanta(level, pos.east(), maxQuanta);
                maxQuanta = getLargerQuanta(level, pos.north(), maxQuanta);
                maxQuanta = getLargerQuanta(level, pos.south(), maxQuanta);
                expQuanta = maxQuanta - 1;
            }

            // decay calculation
            if (expQuanta != quantaRemaining) {
                quantaRemaining = expQuanta;
                if (expQuanta <= 0) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                } else {
                    level.setBlock(pos, withQuanta(expQuanta), 3);
                    scheduleFlow(level, pos);
                    level.updateNeighborsAt(pos, this);
                }
            }
        }
        // (1.7 then re-set a source's metadata to 0; a source here is always level 0.)

        // Flow vertically if possible. 1.7 does this even for a block that has just decayed to air: a hanging
        // remnant then leaves a one-quantum drop below, which decays on its own next tick. Kept as 1.7 did it.
        if (canDisplace(level, vertical(pos))) {
            flowIntoBlock(level, vertical(pos), 1);
            return;
        }

        // Flow outward if possible
        int flowMeta = quanta - quantaRemaining + 1;
        if (flowMeta >= quanta) return;

        if (isSourceBlock(level, pos) || !isFlowingVertically(level, pos)) {
            if (level.getBlockState(counterVertical(pos)).is(this)) flowMeta = 1;
            boolean[] flowTo = getOptimalFlowDirections(level, pos);
            if (flowTo[0]) flowIntoBlock(level, pos.west(), flowMeta);
            if (flowTo[1]) flowIntoBlock(level, pos.east(), flowMeta);
            if (flowTo[2]) flowIntoBlock(level, pos.north(), flowMeta);
            if (flowTo[3]) flowIntoBlock(level, pos.south(), flowMeta);
        }
    }

    private static final Direction[] SIDES = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};

    /** 1.7 {@code getOptimalFlowDirections}: the sides with the cheapest way down within four blocks. */
    protected boolean[] getOptimalFlowDirections(BlockGetter level, BlockPos pos) {
        int[] flowCost = new int[4];
        for (int side = 0; side < 4; side++) {
            flowCost[side] = 1000;
            BlockPos next = pos.relative(SIDES[side]);
            if (!canFlowInto(level, next) || isSourceBlock(level, next)) continue;
            if (canFlowInto(level, vertical(next))) flowCost[side] = 0;
            else flowCost[side] = calculateFlowCost(level, next, 1, side);
        }
        int min = flowCost[0];
        for (int side = 1; side < 4; side++) min = Math.min(min, flowCost[side]);
        boolean[] optimal = new boolean[4];
        for (int side = 0; side < 4; side++) optimal[side] = flowCost[side] == min;
        return optimal;
    }

    private int calculateFlowCost(BlockGetter level, BlockPos pos, int recurseDepth, int side) {
        int cost = 1000;
        for (int adjSide = 0; adjSide < 4; adjSide++) {
            // never straight back: 0/1 and 2/3 are opposite
            if ((adjSide ^ 1) == side) continue;
            BlockPos next = pos.relative(SIDES[adjSide]);
            if (!canFlowInto(level, next) || isSourceBlock(level, next)) continue;
            if (canFlowInto(level, vertical(next))) return recurseDepth;
            if (recurseDepth >= 4) continue;
            cost = Math.min(cost, calculateFlowCost(level, next, recurseDepth + 1, adjSide));
        }
        return cost;
    }

    /** 1.7 {@code flowIntoBlock}: places this fluid with 1.7 metadata {@code meta} where it may displace. */
    protected void flowIntoBlock(Level level, BlockPos pos, int meta) {
        if (meta < 0) return;
        if (displaceIfPossible(level, pos)) level.setBlock(pos, withQuanta(quanta - meta), 3);
    }

    // BlockFluidFinite

    /** 1.7 {@code BlockFluidFinite.getQuantaValue}: air 0, another block -1, else the quanta it holds. */
    private int finiteQuanta(BlockGetter level, BlockPos pos) {
        return getQuantaValue(level, pos);
    }

    /** 1.7 {@code getQuantaValueBelow}: the quanta at {@code pos} if fewer than {@code belowThis}, else -1. */
    private int quantaBelow(BlockGetter level, BlockPos pos, int belowThis) {
        int remaining = finiteQuanta(level, pos);
        return remaining >= belowThis ? -1 : remaining;
    }

    private void setFinite(Level level, BlockPos pos, int remaining, int flags) {
        if (remaining <= 0) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        else level.setBlock(pos, withQuanta(remaining), flags);
    }

    private void flowFinite(ServerLevel level, BlockPos pos, BlockState state) {
        boolean changed = false;
        int quantaRemaining = quantaOf(state);

        // Flow vertically if possible
        int prevRemaining = quantaRemaining;
        quantaRemaining = tryToFlowVerticallyInto(level, pos, quantaRemaining);

        if (quantaRemaining < 1) {
            return;
        } else if (quantaRemaining != prevRemaining) {
            changed = true;
            if (quantaRemaining == 1) {
                setFinite(level, pos, quantaRemaining, 2);
                return;
            }
        } else if (quantaRemaining == 1) {
            return;
        }

        // Flow out if possible
        int lowerthan = quantaRemaining - 1;
        BlockPos[] around = {pos.north(), pos.south(), pos.west(), pos.east()};
        for (BlockPos side : around) {
            if (displaceIfPossible(level, side)) level.setBlock(side, Blocks.AIR.defaultBlockState(), 3);
        }
        int[] amounts = new int[4];
        int total = quantaRemaining;
        int count = 1;
        for (int i = 0; i < 4; i++) {
            amounts[i] = quantaBelow(level, around[i], lowerthan);
            if (amounts[i] >= 0) {
                count++;
                total += amounts[i];
            }
        }

        if (count == 1) {
            if (changed) setFinite(level, pos, quantaRemaining, 2);
            return;
        }

        int each = total / count;
        int rem = total % count;
        for (int i = 0; i < 4; i++) {
            if (amounts[i] < 0) continue;
            int next = each;
            if (rem == count || rem > 1) {
                rem--;
                next++;
            }
            if (next != amounts[i]) {
                setFinite(level, around[i], next, 3);
                level.scheduleTick(around[i], this, tickRate);
            }
            --count;
        }
        if (rem > 0) ++each;
        setFinite(level, pos, each, 2);
    }

    /** 1.7 {@code BlockFluidFinite.tryToFlowVerticallyInto}: returns the quanta left behind. */
    private int tryToFlowVerticallyInto(ServerLevel level, BlockPos pos, int amtToInput) {
        BlockPos other = vertical(pos);
        if (level.isOutsideBuildHeight(other)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return 0;
        }

        int amt = quantaBelow(level, other, quanta);
        if (amt >= 0) {
            amt += amtToInput;
            if (amt > quanta) {
                level.setBlock(other, withQuanta(quanta), 3);
                level.scheduleTick(other, this, tickRate);
                return amt - quanta;
            } else if (amt > 0) {
                level.setBlock(other, withQuanta(amt), 3);
                level.scheduleTick(other, this, tickRate);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                return 0;
            }
            return amtToInput;
        }

        int densityOther = densityAt(level, other);
        if (densityOther == Integer.MAX_VALUE) {
            if (displaceIfPossible(level, other)) {
                level.setBlock(other, withQuanta(amtToInput), 3);
                level.scheduleTick(other, this, tickRate);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                return 0;
            }
            return amtToInput;
        }

        // a lighter fluid below (heavier above, for a gas): swap places
        if (densityDir < 0 ? densityOther < density : densityOther > density) {
            BlockState swapped = level.getBlockState(other);
            level.setBlock(other, withQuanta(amtToInput), 3);
            level.setBlock(pos, swapped, 3);
            level.scheduleTick(other, this, tickRate);
            level.scheduleTick(pos, swapped.getBlock(), swapped.getBlock() instanceof FluidBlock f ? f.tickRate : 1);
            return 0;
        }
        return amtToInput;
    }

    // Buckets (1.7 BucketHandler: sources only)

    private ItemStack pickup(LevelAccessor level, BlockPos pos, BlockState state) {
        Item bucket = fluid.bucketItem();
        if (bucket == null || state.getValue(LEVEL) != 0 || fluid.spec().isFinite()) return ItemStack.EMPTY;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
        return new ItemStack(bucket);
    }

    //? if >=26.1 {
    @Override
    public ItemStack pickupBlock(net.minecraft.world.entity.@Nullable LivingEntity user, LevelAccessor level, BlockPos pos, BlockState state) {
        return pickup(level, pos, state);
    }
    //?} elif >=1.20.5 {
    /*@Override
    public ItemStack pickupBlock(net.minecraft.world.entity.player.@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        return pickup(level, pos, state);
    }
    *///?} else {
    /*@Override
    public ItemStack pickupBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        return pickup(level, pos, state);
    }
    *///?}

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return fluid.source().getPickupSound();
    }
}
