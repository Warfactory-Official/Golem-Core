package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Explosion;
import net.minecraft.server.level.ServerLevel;
import java.util.function.BiConsumer;
//? if >=26.1 {
import net.minecraft.world.level.ScheduledTickAccess;
//?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A {@link Block} that runs its block entity's removal side effects on every version.
 *
 * <p>26.1 calls {@code BlockEntity.preRemoveSideEffects} itself when a block entity goes away, and its
 * default drops the contents of any {@code Container}. Nothing does that below 26.1 (dropping is each
 * block's own business), so a block whose entity holds an inventory silently voids it when broken. The
 * removal hook is also spelled differently per era ({@code affectNeighborsAfterRemoval} versus
 * {@code onRemove}), which is why this is a base class rather than many copies of one guarded override.
 *
 * <p>Extend this instead of {@code Block} for any block with an inventory-bearing block entity. A
 * subclass that needs removal behavior should override {@link #onBlockRemoved}; the version-specific
 * vanilla hook remains owned by this class.
 *
 * @see Compat#preRemoveSideEffects
 */
public class CompatBlock extends Block {

    public CompatBlock(Properties properties) {
        super(properties);
    }

    /**
     * Handles a player's held item being used on this block.
     *
     * <p>Override this instead of Minecraft's version-specific block interaction hook. Returning
     * {@link InteractionResult#PASS} retains vanilla's next interaction step.
     */
    protected InteractionResult onUseItem(BlockState state, Level level, BlockPos pos, Player player,
                                            InteractionHand hand, ItemStack stack, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /** Handles an empty-hand interaction without exposing vanilla's changing callback name. */
    protected InteractionResult onUseWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                 BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /** Handles an entity entering the block without exposing the 26.1 effect-applier parameters. */
    protected void onEntityInside(BlockState state, Level level, BlockPos pos, Entity entity) {}

    /** Handles a player left-clicking (starting to break) the block, 1.7 {@code onBlockClicked}. Both sides. */
    protected void onAttack(BlockState state, Level level, BlockPos pos, Player player) {}

    // Vanilla's attack hook is public on 1.20.1 and protected from 1.21 on; public widens it on every node.
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        super.attack(state, level, pos, player);
        onAttack(state, level, pos, player);
    }

    /** Handles a neighbouring state change without exposing the changing tick-access parameters. */
    protected BlockState onUpdateShape(BlockState state, LevelReader level, BlockTickScheduler ticks, BlockPos pos,
                                       Direction direction, BlockPos neighbourPos,
                                       BlockState neighbourState) {
        return state;
    }

    /** Handles a neighbour notification without exposing its changing orientation/source parameters. */
    protected void onNeighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                     boolean movedByPiston) {}

    /** Supplies comparator strength without exposing the version-specific direction parameter. */
    protected int onAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return 0;
    }

    /**
     * Runs after this block entity's removal side effects and before vanilla's removal hook.
     * Override this instead of a version-specific {@code onRemove}/{@code affectNeighborsAfterRemoval}.
     */
    protected void onBlockRemoved(BlockState state, Level level, BlockPos pos, boolean movedByPiston) {}

    /** Runs before a player removes this block, across the return-type change in vanilla's hook. */
    protected void onPlayerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {}

    /**
     * Handles an explosion before vanilla removes this block. Returning true consumes the native fallback.
     * The item consumer is absent before 26.1, where vanilla owns loot emission itself.
     */
    protected boolean onExplosion(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
                                  BiConsumer<ItemStack, BlockPos> onHit) {
        return false;
    }

    /** Forwards an explosion to another block's own vanilla hook without exposing its era-specific name. */
    public final void forwardExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
                                          BiConsumer<ItemStack, BlockPos> onHit) {
        //? if >=26.1 {
        onExplosionHit(state, level, pos, explosion, onHit);
        //?} else {
        /*// Vanilla's 26.1 default in this era's spelling: a hook that answers false loses the block with
        // its explosion drops, then wasExploded runs on the empty cell as it always did.
        if (!onExplosion(state, level, pos, explosion, (stack, dropPos) -> {})) {
            level.destroyBlock(pos, dropFromExplosion(explosion));
            wasExploded(level, pos, explosion);
        }
        *///?}
    }

    /**
     * The stack a pick-block (middle click) of this block yields. Override this instead of vanilla's
     * {@code getCloneItemStack}, whose parameters differ per era (26.1 adds an {@code includeData} flag,
     * 1.20.1 passes a {@code BlockGetter}); an override of the wrong spelling compiles as a plain overload
     * and is silently never called. The default is vanilla's own: a plain stack of this block's item.
     */
    protected ItemStack onPickBlock(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(this);
    }

    /** The pick-block stack for this position, through {@link #onPickBlock}; callable on every era. */
    public final ItemStack pickBlockStack(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        return onPickBlock(level, pos, state);
    }

    //? if >=26.1 {
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return onPickBlock(level, pos, state);
    }
    //?} elif >=1.21.1 {
    /*@Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return onPickBlock(level, pos, state);
    }
    *///?} else {
    /*@Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        return onPickBlock(level, pos, state);
    }
    *///?}

    /** Whether vanilla will suppress the normal block drop for this player. */
    protected static boolean playerPreventsBlockDrops(Player player) {
        //? if >=26.1 {
        return player.preventsBlockDrops();
        //?} else {
        /*return player.getAbilities().instabuild;
        *///?}
    }

    //? if <1.21.1 {
    /*@Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        onPlayerWillDestroy(level, pos, state, player);
        super.playerWillDestroy(level, pos, state, player);
    }
    *///?}

    //? if >=1.21.1 {
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        onPlayerWillDestroy(level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    //?}

    //? if >=26.1 {
    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
                                  BiConsumer<ItemStack, BlockPos> onHit) {
        // a trigger-only blast (a wind charge) removes nothing, so the removal hook is not asked
        if (explosion.getBlockInteraction() == Explosion.BlockInteraction.TRIGGER_BLOCK
                || !onExplosion(state, level, pos, explosion, onHit)) {
            super.onExplosionHit(state, level, pos, explosion, onHit);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = onUseItem(state, level, pos, player, hand, stack, hit);
        // PASS must become TRY_WITH_EMPTY_HAND here. ServerPlayerGameMode calls useItemOn for EVERY
        // right-click (empty hand included) and only reaches useWithoutItem when the result is
        // `instanceof InteractionResult.TryEmptyHandInteraction`: a plain PASS ends the interaction.
        // Returning PASS therefore makes every block that only overrides onUseWithoutItem completely
        // inert: no menu, no error, nothing in the log. The 1.21.1 branch below already maps PASS onto
        // its equivalent (PASS_TO_DEFAULT_BLOCK_INTERACTION); this is the same mapping for 26.1.
        return result instanceof InteractionResult.Pass ? InteractionResult.TRY_WITH_EMPTY_HAND : result;
    }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return onUseWithoutItem(state, level, pos, player, hit);
    }
    //?} else if >=1.21.1 {
    /*@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                                    BlockPos pos, Player player,
                                                                    InteractionHand hand, BlockHitResult hit) {
        return switch (onUseItem(state, level, pos, player, hand, stack, hit)) {
            case SUCCESS -> net.minecraft.world.ItemInteractionResult.SUCCESS;
            case SUCCESS_NO_ITEM_USED -> net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            case CONSUME -> net.minecraft.world.ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> net.minecraft.world.ItemInteractionResult.CONSUME_PARTIAL;
            case FAIL -> net.minecraft.world.ItemInteractionResult.FAIL;
            case PASS -> net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return onUseWithoutItem(state, level, pos, player, hit);
    }
    *///?} else {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
         ItemStack stack = player.getItemInHand(hand);
         InteractionResult result = onUseItem(state, level, pos, player, hand, stack, hit);
         return result == InteractionResult.PASS ? onUseWithoutItem(state, level, pos, player, hit) : result;
    }
    *///?}

    @Override
    //? if >=26.1 {
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Compat.preRemoveSideEffects(level, pos, state);
        onBlockRemoved(state, level, pos, movedByPiston);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
    //?} else {
    /*public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Only when the block is actually going away, not on a state change that keeps it; otherwise a
         // machine would drop its inventory every time one of its blockstate properties flipped.
         if (!state.is(newState.getBlock())) {
             Compat.preRemoveSideEffects(level, pos, state);
             onBlockRemoved(state, level, pos, movedByPiston);
         }
         super.onRemove(state, level, pos, newState, movedByPiston);
    }
    *///?}

    //? if >=1.21 && <26.1 {
    /*/^*
     * 1.21's vanilla hook, called before the block is removed, with the same contract as 26.1's: a hook
     * that answers true owns the block's removal, and a trigger-only blast skips it. Both loaders route
     * explosions through it in this era.
     ^/
    @Override
    protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion,
                                  BiConsumer<ItemStack, BlockPos> onHit) {
        if (level instanceof ServerLevel server && explosion.getBlockInteraction() != Explosion.BlockInteraction.TRIGGER_BLOCK
                && onExplosion(state, server, pos, explosion, onHit)) {
            return;
        }
        super.onExplosionHit(state, level, pos, explosion, onHit);
    }
    *///?} elif forge {
    /*/^*
     * Forge's pre-removal hook carries the state; vanilla's {@code wasExploded} runs after the block is
     * already air, which is why the HBM hook is routed from here on Forge 1.20.1.
     ^/
    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        // Same contract as 26.1 onExplosionHit: a hook that answers true owns the block's removal.
        if (level instanceof ServerLevel server
                && onExplosion(state, server, pos, explosion, (stack, dropPos) -> {})) {
            return;
        }
        super.onBlockExploded(state, level, pos, explosion);
    }
    *///?} elif fabric {
    /*/^*
     * Fabric 1.20.1 has no pre-removal hook at all, vanilla calls {@code wasExploded} after the block
     * is air, so {@code MixinExplosionBlockHook} calls this from the explosion's block loop instead.
     * Returns true when the hook owns the block's removal.
     ^/
    public final boolean golem$explosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion) {
        return onExplosion(state, level, pos, explosion, (stack, dropPos) -> {});
    }
    *///?}

    //? if >=26.1 {
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.Entity entity,
                                net.minecraft.world.entity.InsideBlockEffectApplier effects, boolean precise) {
        super.entityInside(state, level, pos, entity, effects, precise);
        onEntityInside(state, level, pos, entity);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     net.minecraft.core.Direction direction, BlockPos neighbourPos,
                                     BlockState neighbourState, net.minecraft.util.RandomSource random) {
        return onUpdateShape(state, level, ticks::scheduleTick, pos, direction, neighbourPos, neighbourState);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        onNeighborChanged(state, level, pos, neighbourBlock, movedByPiston);
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos,
                                        net.minecraft.core.Direction direction) {
        return onAnalogOutputSignal(state, level, pos);
    }
    //?} else {
    /*@Override
    public void entityInside(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.Entity entity) {
        super.entityInside(state, level, pos, entity);
        onEntityInside(state, level, pos, entity);
    }

    @Override
    public BlockState updateShape(BlockState state, net.minecraft.core.Direction direction,
                                  BlockState neighbourState, net.minecraft.world.level.LevelAccessor level,
                                  BlockPos pos, BlockPos neighbourPos) {
        return onUpdateShape(state, level, level::scheduleTick, pos, direction, neighbourPos, neighbourState);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                BlockPos fromPos, boolean movedByPiston) {
        onNeighborChanged(state, level, pos, neighbourBlock, movedByPiston);
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return onAnalogOutputSignal(state, level, pos);
    }
    *///?}

    /**
     * An entity landed on this block after falling {@code fallDistance} blocks (the old {@code onFallenUpon}).
     * The default is vanilla's: fall damage. An override that keeps fall damage calls
     * {@link #defaultFallOn}. Vanilla's {@code fallOn} took the distance as a {@code float} until 26.1 and as a
     * {@code double} since; this hook takes the {@code double} on every node.
     */
    protected void onFallOn(Level level, BlockState state, BlockPos pos, net.minecraft.world.entity.Entity entity,
                            double fallDistance) {
        defaultFallOn(level, state, pos, entity, fallDistance);
    }

    /** Vanilla's {@code fallOn}: the entity takes its fall damage. */
    protected final void defaultFallOn(Level level, BlockState state, BlockPos pos,
                                       net.minecraft.world.entity.Entity entity, double fallDistance) {
        //? if >=26.1 {
        super.fallOn(level, state, pos, entity, fallDistance);
        //?} else {
        /*super.fallOn(level, state, pos, entity, (float) fallDistance);
        *///?}
    }

    //? if >=26.1 {
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, net.minecraft.world.entity.Entity entity,
                       double fallDistance) {
        onFallOn(level, state, pos, entity, fallDistance);
    }
    //?} else {
    /*@Override
    public void fallOn(Level level, BlockState state, BlockPos pos, net.minecraft.world.entity.Entity entity,
                       float fallDistance) {
        onFallOn(level, state, pos, entity, fallDistance);
    }
    *///?}

    // Soil (1.7 canSustainPlant)

    /**
     * Whether a plant may stand on this block at {@code soilPos} (1.7 {@code Block.canSustainPlant}): {@link
     * dev.golem.core.hook.Verdict#ALLOW} keeps it, {@link dev.golem.core.hook.Verdict#DENY} pops it,
     * {@link dev.golem.core.hook.Verdict#PASS} (the default) leaves it to the plant, as vanilla. {@code plant} is the
     * plant's state above. NeoForge/Forge ask through their soil hook; on Fabric {@code MixinVegetationSoil} asks
     * from the plant's survival check (every {@code BushBlock}/{@code VegetationBlock}). Crops keep their light
     * requirement except on NeoForge, whose crops take a soil answer as final.
     */
    protected dev.golem.core.hook.Verdict onSustainPlant(net.minecraft.world.level.BlockGetter level, BlockPos soilPos,
                                                         BlockState soil, BlockState plant) {
        return dev.golem.core.hook.Verdict.PASS;
    }

    /** {@link #onSustainPlant}, for the loader glue. */
    public final dev.golem.core.hook.Verdict sustainsPlant(net.minecraft.world.level.BlockGetter level, BlockPos soilPos,
                                                           BlockState soil, BlockState plant) {
        return onSustainPlant(level, soilPos, soil, plant);
    }

    //? if neoforge && >=26.1 {
    @Override
    public net.minecraft.util.TriState canSustainPlant(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos soilPosition, net.minecraft.core.Direction facing,
            BlockState plant) {
        return switch (onSustainPlant(level, soilPosition, state, plant)) {
            case ALLOW -> net.minecraft.util.TriState.TRUE;
            case DENY -> net.minecraft.util.TriState.FALSE;
            case PASS -> super.canSustainPlant(state, level, soilPosition, facing, plant);
        };
    }
    //?} elif neoforge {
    /*@Override
    public net.neoforged.neoforge.common.util.TriState canSustainPlant(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos soilPosition, net.minecraft.core.Direction facing,
            BlockState plant) {
        return switch (onSustainPlant(level, soilPosition, state, plant)) {
            case ALLOW -> net.neoforged.neoforge.common.util.TriState.TRUE;
            case DENY -> net.neoforged.neoforge.common.util.TriState.FALSE;
            case PASS -> super.canSustainPlant(state, level, soilPosition, facing, plant);
        };
    }
    *///?} elif forge {
    /*@Override
    public boolean canSustainPlant(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.core.Direction facing, net.minecraftforge.common.IPlantable plantable) {
        dev.golem.core.hook.Verdict verdict = onSustainPlant(level, pos, state, plantable.getPlant(level, pos.relative(facing)));
        return verdict == dev.golem.core.hook.Verdict.PASS ? super.canSustainPlant(state, level, pos, facing, plantable)
                : verdict == dev.golem.core.hook.Verdict.ALLOW;
    }
    *///?}

    // Light opacity 0 (1.7 lightOpacity)

    /**
     * Whether this state is fully transparent to light, 1.7's {@code lightOpacity} 0 (every block that was not an
     * opaque cube): it dampens neither block nor sky light, and sky light passes down through it undiminished.
     * Vanilla derives both from the shape and the occlusion flag; the default keeps vanilla's answer. Must depend
     * on the state alone: vanilla caches it per state. A waterlogged state keeps the water's dampening.
     */
    protected boolean isLightTransparent(BlockState state) {
        return false;
    }

    //? if >=26.1 {
    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return isLightTransparent(state) && state.getFluidState().isEmpty() || super.propagatesSkylightDown(state);
    }

    @Override
    protected int getLightDampening(BlockState state) {
        return isLightTransparent(state) && state.getFluidState().isEmpty() ? 0 : super.getLightDampening(state);
    }
    //?} elif >=1.21.1 {
    /*@Override
    protected boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return isLightTransparent(state) && state.getFluidState().isEmpty() || super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    protected int getLightBlock(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return isLightTransparent(state) && state.getFluidState().isEmpty() ? 0 : super.getLightBlock(state, level, pos);
    }
    *///?} else {
    /*@Override
    public boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return isLightTransparent(state) && state.getFluidState().isEmpty() || super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getLightBlock(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return isLightTransparent(state) && state.getFluidState().isEmpty() ? 0 : super.getLightBlock(state, level, pos);
    }
    *///?}

    //? if forgelike {
    /**
     * Forge-like loaders read fire odds from the block (vanilla's table is private there), so the values
     * stated through {@link Fires#setFlammable} are answered here. Fabric reads its registry instead.
     */
    @Override
    public int getFlammability(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                               net.minecraft.core.Direction direction) {
        int value = Fires.statedFlammability(this);
        return value >= 0 ? value : super.getFlammability(state, level, pos, direction);
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                  net.minecraft.core.Direction direction) {
        int value = Fires.statedEncouragement(this);
        return value >= 0 ? value : super.getFireSpreadSpeed(state, level, pos, direction);
    }
    //?}
}
