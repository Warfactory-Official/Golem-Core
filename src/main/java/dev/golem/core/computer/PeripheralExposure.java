package dev.golem.core.computer;

//? if computercraft {
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.golem.core.platform.Platform;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Publishes a mod's blocks as CC: Tweaked peripherals, through whichever lookup the loader's CC build reads.
 *
 * <ul>
 *   <li>NeoForge (26.1 and 1.21.1): the {@code PeripheralCapability} block capability, registered in
 *       {@code RegisterCapabilitiesEvent} on the blocks {@code carriers} accepts.</li>
 *   <li>1.20.1 Forge: {@code ForgeComputerCraftAPI.registerPeripheralProvider}, asked for any position, so a
 *       block with no block entity (a multiblock's folded cell) answers too.</li>
 *   <li>Fabric: a fallback on {@code PeripheralLookup}, the Fabric API block lookup CC reads.</li>
 * </ul>
 *
 * <p>The peripheral API itself ({@code IPeripheral}, {@code @LuaFunction}) is identical on every node; only this
 * registration differs. CC: Tweaked is optional: without it {@link #exposeBlocks} does nothing and loads none of
 * its types. Call once from common init, while the mod bus still accepts listeners.
 */
public final class PeripheralExposure {

    public static final String MOD_ID = "computercraft";

    private PeripheralExposure() {}

    /** Whether CC: Tweaked is loaded. The caller's own CC-typed classes may be touched only when this is true. */
    public static boolean available() {
        return Platform.isModLoaded(MOD_ID);
    }

    /** Asks {@code ports} for a peripheral on the blocks {@code carriers} accepts. */
    public static void exposeBlocks(PeripheralPorts ports, Predicate<Block> carriers) {
        if (!available()) return;
        //? if neoforge {
        NeoPeripherals.exposeBlocks(ports, carriers);
        //?} elif forge {
        /*ForgePeripherals.exposeBlocks(ports, carriers);
        *///?} else {
        /*FabricPeripherals.exposeBlocks(ports, carriers);
        *///?}
    }

    /**
     * The peripheral CC would find at {@code pos} through {@code side}, asked through the same loader lookup CC
     * reads (NeoForge capability, Fabric {@code PeripheralLookup}). For tests and probes; null without CC.
     *
     * <p>1.20.1 Forge has no public query for provider peripherals, so there this asks the providers registered
     * through {@link #exposeBlocks}, which is the code CC's own lookup calls.
     */
    public static @Nullable IPeripheral find(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        if (!available()) return null;
        //? if neoforge {
        return NeoPeripherals.find(level, pos, side);
        //?} elif forge {
        /*return ForgePeripherals.find(level, pos, side);
        *///?} else {
        /*return FabricPeripherals.find(level, pos, side);
        *///?}
    }
}
//?}
