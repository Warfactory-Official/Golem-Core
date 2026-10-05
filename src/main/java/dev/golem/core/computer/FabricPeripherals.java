package dev.golem.core.computer;

//? if computercraft && fabric {
/*import dan200.computercraft.api.peripheral.IPeripheral;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

// Fabric: CC reads its PeripheralLookup, a Fabric API BlockApiLookup keyed by the touched side. BlockApiLookup.get
// hands back the one lookup registered under an id, so asking for "computercraft:peripheral" with CC's own API and
// context classes is CC's lookup itself; Core then needs only CC's Minecraft-free core-api, not the fabric-api jar
// Loom cannot remap on the obfuscated nodes. A fallback is asked for every block, blocks without a block entity
// (folded cells) included, so the carrier test runs per query.
final class FabricPeripherals {

    private FabricPeripherals() {}

    private static BlockApiLookup<IPeripheral, Direction> lookup() {
        return BlockApiLookup.get(dev.golem.core.Ids.of(PeripheralExposure.MOD_ID, "peripheral"),
                IPeripheral.class, Direction.class);
    }

    static @Nullable IPeripheral find(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        return lookup().find(level, pos, side);
    }

    static void exposeBlocks(PeripheralPorts ports, Predicate<Block> carriers) {
        lookup().registerFallback((level, pos, state, be, side) ->
                level instanceof ServerLevel server && carriers.test(state.getBlock())
                        ? ports.at(server, pos, state, be, side) : null);
    }
}
*///?}
