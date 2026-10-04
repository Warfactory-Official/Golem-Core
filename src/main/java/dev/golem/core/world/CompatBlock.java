package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
 * default drops the contents of any {@code Container}. Nothing does that below 26.1 — dropping is each
 * block's own business — so a block whose entity holds an inventory silently voids it when broken. The
 * hook that replaced it is also spelled differently per era ({@code affectNeighborsAfterRemoval} versus
 * {@code onRemove}), which is the other half of why this is worth a base class rather than fifteen
 * copies of the same guarded override.
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
    protected void onEntityInside(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.Entity entity) {}

    /** Handles a neighbouring state change without exposing the changing tick-access parameters. */
    protected BlockState onUpdateShape(BlockState state, LevelReader level, BlockTickScheduler ticks, BlockPos pos,
                                       net.minecraft.core.Direction direction, BlockPos neighbourPos,
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
        if (!onExplosion(state, level, pos, explosion, onHit)) {
            super.onExplosionHit(state, level, pos, explosion, onHit);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = onUseItem(state, level, pos, player, hand, stack, hit);
        // PASS must become TRY_WITH_EMPTY_HAND here. ServerPlayerGameMode calls useItemOn for EVERY
        // right-click (empty hand included) and only reaches useWithoutItem when the result is
        // `instanceof InteractionResult.TryEmptyHandInteraction` — a plain PASS ends the interaction.
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
        // Only when the block is actually going away, not on a state change that keeps it — otherwise a
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
     * that answers true owns the block's removal. Both loaders route explosions through it in this era.
     ^/
    @Override
    protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion,
                                  BiConsumer<ItemStack, BlockPos> onHit) {
        if (level instanceof ServerLevel server && onExplosion(state, server, pos, explosion, onHit)) {
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
     * Fabric 1.20.1 has no pre-removal hook at all — vanilla calls {@code wasExploded} after the block
     * is air — so {@code MixinExplosionBlockHook} calls this from the explosion's block loop instead.
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
