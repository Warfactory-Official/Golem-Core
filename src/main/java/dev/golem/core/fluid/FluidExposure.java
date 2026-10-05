package dev.golem.core.fluid;

import java.util.function.Predicate;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Publishes a mod's fluid machines and container items to the loader's standard fluid capability, so other
 * mods' pipes, tanks, buckets and probes can see and move the mod's fluids.
 *
 * <ul>
 *   <li>26.1 NeoForge: {@code Capabilities.Fluid.BLOCK}/{@code ITEM} ({@code ResourceHandler<FluidResource>},
 *       transactional; block moves are staged and applied on the root commit).</li>
 *   <li>1.21.1 NeoForge: {@code Capabilities.FluidHandler.BLOCK}/{@code ITEM} ({@code IFluidHandler}, simulate flag).</li>
 *   <li>1.20.1 Forge: {@code ForgeCapabilities.FLUID_HANDLER}/{@code FLUID_HANDLER_ITEM}, attached to block
 *       entities and item stacks; a block without a block entity exposes nothing there.</li>
 *   <li>Fabric: {@code FluidStorage.SIDED}/{@code ITEM} (Transfer API, transactional, droplets; see
 *       {@link FluidUnits}).</li>
 * </ul>
 *
 * <p>Call each once from common init, while the mod bus is still accepting listeners. The carrier predicates
 * are evaluated when the loader asks (at capability registration on NeoForge, per lookup elsewhere), so they
 * may read registries the caller's own registration has not filled yet.
 */
public final class FluidExposure {

    private FluidExposure() {}

    /** Exposes every face {@code ports} answers for, on the blocks {@code carriers} accepts. */
    public static void exposeBlocks(FluidPorts ports, Predicate<Block> carriers) {
        //? if neoforge && >=26.1 {
        NeoTransferFluids.exposeBlocks(ports, carriers);
        //?} elif neoforge {
        /*NeoLegacyFluids.exposeBlocks(ports, carriers);
        *///?} elif forge {
        /*ForgeFluids.exposeBlocks(ports, carriers);
        *///?} else {
        /*FabricTransferFluids.exposeBlocks(ports, carriers);
        *///?}
    }

    /** Exposes {@code contract} on the items {@code carriers} accepts. */
    public static void exposeItems(FluidContainer contract, Predicate<Item> carriers) {
        //? if neoforge && >=26.1 {
        NeoTransferFluids.exposeItems(contract, carriers);
        //?} elif neoforge {
        /*NeoLegacyFluids.exposeItems(contract, carriers);
        *///?} elif forge {
        /*ForgeFluids.exposeItems(contract, carriers);
        *///?} else {
        /*FabricTransferFluids.exposeItems(contract, carriers);
        *///?}
    }
}
