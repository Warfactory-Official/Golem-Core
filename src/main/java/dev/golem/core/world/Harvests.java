package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Server block harvesting with an explicit tool, including tools held in the off hand. */
public final class Harvests {

    private Harvests() {}

    /**
     * Runs the vanilla survival destroy sequence without asking the game mode for its main-hand stack.
     * The caller supplies the source harvest decision because some tools intentionally bypass the
     * ordinary vanilla tool-tier predicate.
     */
    public static boolean destroyBlock(ServerPlayer player, BlockPos pos, ItemStack tool,
                                       InteractionHand hand, boolean canHarvest) {
        ServerLevel level = (ServerLevel) player.level();
        BlockState original = level.getBlockState(pos);
        if (original.isAir() || !fireBreakEvent(level, player, pos, original)) return false;

        Block block = original.getBlock();
        if (block instanceof GameMasterBlock && !player.canUseGameMasterBlocks()) return false;

        GameType gameType = player.gameMode.getGameModeForPlayer();
        if (player.blockActionRestricted(level, pos, gameType)) return false;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        BlockState state = original;
        if (player.gameMode.isCreative()) {
            remove(level, pos, state, false, player, ItemStack.EMPTY);
            return true;
        }

        ItemStack beforeMine = tool.copy();
        tool.mineBlock(level, state, pos, player);
        boolean removed = remove(level, pos, state, canHarvest, player, beforeMine);
        if (canHarvest && removed) {
            int dropsBefore = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(0.75D)).size();
            block.playerDestroy(level, player, pos, state, blockEntity, beforeMine);
            // Keep the vanilla loot contract when a custom callback stack is not visible to the block's
            // legacy playerDestroy implementation.
            if (level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(0.75D)).size() == dropsBefore) {
                Block.dropResources(state, level, pos, blockEntity, player, beforeMine);
            }
        }
        if (tool.isEmpty() && !beforeMine.isEmpty()) notifyToolBroken(player, beforeMine, hand);
        return removed;
    }

    private static boolean remove(ServerLevel level, BlockPos pos, BlockState state, boolean canHarvest,
                                   Player player, ItemStack tool) {
        // Never let the level drop the loot here: the harvest branch drops through playerDestroy with the
        // real tool, and a second loot roll from destroyBlock(pos, true) doubled every drop on 1.20.1.
        return level.destroyBlock(pos, false, player);
    }

    /** Posts the loader's block-break event (or Fabric's BEFORE callback); false when a listener cancels. */
    public static boolean fireBreakEvent(ServerLevel level, ServerPlayer player, BlockPos pos,
                                          BlockState state) {
        GameType gameType = player.gameMode.getGameModeForPlayer();
        boolean restricted = !level.mayInteract(player, pos)
                || player.blockActionRestricted(level, pos, gameType)
                || state.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks();

        //? if neoforge {
        net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event =
                new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level, pos, state, player);
        event.setCanceled(restricted);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
        //?} elif forge {
        /*net.minecraftforge.event.level.BlockEvent.BreakEvent event =
                new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, pos, state, player);
        event.setCanceled(restricted);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
        return !event.isCanceled();
        *///?} else {
        /*if (restricted) return false;
        return net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker()
                .beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos));
        *///?}
    }

    private static void notifyToolBroken(ServerPlayer player, ItemStack tool, InteractionHand hand) {
        //? if neoforge {
        net.neoforged.neoforge.event.EventHooks.onPlayerDestroyItem(player, tool, hand);
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new net.minecraftforge.event.entity.player.PlayerDestroyItemEvent(player, tool, hand));
        *///?} else {
         //?}
    }
}
