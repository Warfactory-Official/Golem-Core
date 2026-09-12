package dev.golem.core.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Block-entity type construction across loader and constructor differences, and the one place a
 * block entity's ticker is named.
 *
 * <h2>Tickers</h2>
 *
 * <p>Vanilla asks the <em>block</em> for a ticker and hands it the block entity's type, which leaves
 * every block re-deriving a fact that belongs to the block entity: whether it ticks, on which side,
 * and through which method. Done per block that is an override, a side test, a type identity check
 * and a private unchecked cast — and it was written 142 times here, with the cast copied 114 times.
 *
 * <p>The ticker is declared where the type is built instead:
 *
 * <pre>{@code
 * BLOCK_ENTITIES.register("machine_diesel", () -> BlockEntities.serverTicking(
 *         TileEntityMachineDiesel::new, TileEntityMachineDiesel::serverTick,
 *         ModMachineBlocks.machine_diesel.get()));
 * }</pre>
 *
 * <p>and the block says only that it is a {@link TickingBlock}, whose default {@code getTicker}
 * answers from the type it was handed. That makes the identity check vanish rather than move: a
 * block backing more than one block-entity type — a pump that is steam or electric, a base class
 * shared by six radio torches — used to need a branch to pick which type it was looking at, and now
 * gets the right ticker because the lookup is keyed on the type vanilla already resolved.
 */
public final class BlockEntities {

    private static final Map<BlockEntityType<?>, BlockEntityTicker<?>> SERVER_TICKERS =
            new ConcurrentHashMap<>();
    private static final Map<BlockEntityType<?>, BlockEntityTicker<?>> CLIENT_TICKERS =
            new ConcurrentHashMap<>();

    private BlockEntities() {}

    @FunctionalInterface
    public interface Factory<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }

    public static <T extends BlockEntity> BlockEntityType<T> type(Factory<T> factory, Block... blocks) {
        //? if fabric {
        /*return net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder
                .<T>create(factory::create, blocks).build();
        *///?} elif neoforge && >=26.1 {
        return new BlockEntityType<>(factory::create, java.util.Set.of(blocks));
        //?} elif neoforge {
        /*return new BlockEntityType<T>(factory::create, java.util.Set.of(blocks), null);
        *///?} elif forge {
        /*return BlockEntityType.Builder.of(factory::create, blocks).build(null);
        *///?}
    }

    /** A type that ticks on the server only, which is almost every machine. */
    public static <T extends BlockEntity> BlockEntityType<T> serverTicking(
            Factory<T> factory, BlockEntityTicker<? super T> serverTick, Block... blocks) {
        return ticking(factory, null, serverTick, blocks);
    }

    /** A type that ticks on the client only: an animation or a sound with no server state behind it. */
    public static <T extends BlockEntity> BlockEntityType<T> clientTicking(
            Factory<T> factory, BlockEntityTicker<? super T> clientTick, Block... blocks) {
        return ticking(factory, clientTick, null, blocks);
    }

    /**
     * A type with a ticker on each side. Either may be null, and both being null is
     * {@link #type(Factory, Block...)} — a block entity that holds state and never ticks.
     */
    public static <T extends BlockEntity> BlockEntityType<T> ticking(
            Factory<T> factory, BlockEntityTicker<? super T> clientTick,
            BlockEntityTicker<? super T> serverTick, Block... blocks) {
        BlockEntityType<T> type = type(factory, blocks);
        if (clientTick != null) {
            CLIENT_TICKERS.put(type, clientTick);
        }
        if (serverTick != null) {
            SERVER_TICKERS.put(type, serverTick);
        }
        return type;
    }

    /**
     * The ticker declared for this type on this side, or null.
     *
     * <p>The cast is the one {@code createTickerHelper} used to make in every block that had a
     * ticker. It is sound for the same reason it was there: the only way into the map is
     * {@link #ticking}, which binds a {@code BlockEntityTicker<? super T>} to the
     * {@code BlockEntityType<T>} built in the same call.
     */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> BlockEntityTicker<T> ticker(BlockEntityType<T> type, boolean client) {
        return (BlockEntityTicker<T>) (client ? CLIENT_TICKERS : SERVER_TICKERS).get(type);
    }

    /** Whether this type was declared with a ticker on either side; for coverage tests. */
    public static boolean ticks(BlockEntityType<?> type) {
        return SERVER_TICKERS.containsKey(type) || CLIENT_TICKERS.containsKey(type);
    }

    /** How many types were declared with a ticker on either side; for coverage tests. */
    public static int tickingTypes() {
        java.util.Set<BlockEntityType<?>> types = new java.util.HashSet<>(SERVER_TICKERS.keySet());
        types.addAll(CLIENT_TICKERS.keySet());
        return types.size();
    }

    /** Whether this type ticks on the given side; for coverage tests. */
    public static boolean ticks(BlockEntityType<?> type, boolean client) {
        return (client ? CLIENT_TICKERS : SERVER_TICKERS).containsKey(type);
    }

    /** Saves a block entity with its type and position without exposing the registry-aware signature split. */
    public static CompoundTag save(BlockEntity blockEntity, HolderLookup.Provider registries) {
        //? if >=1.20.5 {
        return blockEntity.saveWithFullMetadata(registries);
        //?} else {
        /*return blockEntity.saveWithFullMetadata();
        *///?}
    }

    /** Recreates a registered block entity from custom data across the loadStatic signature split. */
    public static BlockEntity load(BlockPos pos, BlockState state, CompoundTag tag,
                                   HolderLookup.Provider registries) {
        //? if >=1.20.5 {
        return BlockEntity.loadStatic(pos, state, tag, registries);
        //?} else {
        /*return BlockEntity.loadStatic(pos, state, tag);
        *///?}
    }
}
