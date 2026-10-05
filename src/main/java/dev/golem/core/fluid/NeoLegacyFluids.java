package dev.golem.core.fluid;

//? if neoforge && <26.1 {
/*import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jspecify.annotations.Nullable;

// 1.21.1 NeoForge: Capabilities.FluidHandler.BLOCK and ITEM, an IFluidHandler in mB whose simulate flag asks
// without changing anything, so nothing is staged. Item handlers work on one item; a stack of more answers
// reads but moves nothing, as NeoForge's own bucket wrapper does.
final class NeoLegacyFluids {

    private NeoLegacyFluids() {}

    static void exposeBlocks(FluidPorts ports, Predicate<Block> carriers) {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener((RegisterCapabilitiesEvent event) -> {
            Block[] blocks = BuiltInRegistries.BLOCK.stream().filter(carriers).toArray(Block[]::new);
            if (blocks.length == 0) return;
            event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) ->
                    level instanceof ServerLevel server ? BlockHandler.of(ports, server, pos, side) : null, blocks);
        });
    }

    static void exposeItems(FluidContainer contract, Predicate<Item> carriers) {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener((RegisterCapabilitiesEvent event) -> {
            Item[] items = BuiltInRegistries.ITEM.stream().filter(carriers).toArray(Item[]::new);
            if (items.length == 0) return;
            event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) ->
                    contract.handles(stack) ? new ItemHandler(contract, stack) : null, items);
        });
    }

    static @Nullable FluidView find(Level level, BlockPos pos, @Nullable Direction side) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        return handler == null ? null : new View(handler);
    }

    static @Nullable FluidView findItem(ItemStack stack) {
        if (stack.isEmpty()) return null;
        IFluidHandlerItem handler = stack.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        return handler == null ? null : new View(handler);
    }

    private static IFluidHandler.FluidAction action(boolean simulate) {
        return simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
    }

    private static final class View implements FluidView {

        private final IFluidHandler handler;

        View(IFluidHandler handler) {
            this.handler = handler;
        }

        @Override public Object handlerIdentity() { return handler; }
        @Override public int tanks() { return handler.getTanks(); }
        @Override public Fluid fluid(int tank) { FluidStack stack = handler.getFluidInTank(tank); return stack.isEmpty() ? Fluids.EMPTY : stack.getFluid(); }
        @Override public long amount(int tank) { return handler.getFluidInTank(tank).getAmount(); }
        @Override public long capacity(int tank) { return handler.getTankCapacity(tank); }

        @Override
        public long insert(Fluid fluid, long mb, boolean simulate) {
            if (fluid == Fluids.EMPTY || mb <= 0L) return 0L;
            return handler.fill(new FluidStack(fluid, FluidUnits.saturate(mb)), action(simulate));
        }

        @Override
        public long extract(Fluid fluid, long mb, boolean simulate) {
            if (fluid == Fluids.EMPTY || mb <= 0L) return 0L;
            return handler.drain(new FluidStack(fluid, FluidUnits.saturate(mb)), action(simulate)).getAmount();
        }

        @Override
        public ItemStack container() {
            return handler instanceof IFluidHandlerItem item ? item.getContainer() : ItemStack.EMPTY;
        }
    }

    private static final class BlockHandler implements IFluidHandler {

        private final FluidPorts ports;
        private final ServerLevel level;
        private final BlockPos pos;
        private final @Nullable Direction side;
        private FluidMachine face;

        private BlockHandler(FluidPorts ports, ServerLevel level, BlockPos pos, @Nullable Direction side, FluidMachine face) {
            this.ports = ports;
            this.level = level;
            this.pos = pos;
            this.side = side;
            this.face = face;
        }

        static @Nullable BlockHandler of(FluidPorts ports, ServerLevel level, BlockPos pos, @Nullable Direction side) {
            FluidMachine face = ports.at(level, pos, side);
            return face == null ? null : new BlockHandler(ports, level, pos.immutable(), side, face);
        }

        private @Nullable FluidMachine face() {
            if (face.valid()) return face;
            FluidMachine fresh = ports.at(level, pos, side);
            if (fresh != null) face = fresh;
            return fresh;
        }

        @Override
        public int getTanks() {
            FluidMachine face = face();
            return face == null ? 0 : face.tanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            FluidMachine face = face();
            if (face == null || tank < 0 || tank >= face.tanks()) return FluidStack.EMPTY;
            Fluid fluid = face.fluid(tank);
            long amount = face.amount(tank);
            return fluid == Fluids.EMPTY || amount <= 0L ? FluidStack.EMPTY : new FluidStack(fluid, FluidUnits.saturate(amount));
        }

        @Override
        public int getTankCapacity(int tank) {
            FluidMachine face = face();
            return face == null || tank < 0 || tank >= face.tanks() ? 0 : FluidUnits.saturate(face.capacity(tank));
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            FluidMachine face = face();
            return face != null && !stack.isEmpty() && tank >= 0 && tank < face.tanks() && face.accepts(tank, stack.getFluid());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            FluidMachine face = face();
            if (face == null || resource.isEmpty()) return 0;
            return FluidUnits.saturate(FluidStaging.insertNow(face, resource.getFluid(), resource.getAmount(), action.simulate()));
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            FluidMachine face = face();
            if (face == null || resource.isEmpty()) return FluidStack.EMPTY;
            long moved = FluidStaging.extractNow(face, resource.getFluid(), resource.getAmount(), action.simulate());
            return moved <= 0L ? FluidStack.EMPTY : new FluidStack(resource.getFluid(), FluidUnits.saturate(moved));
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            FluidMachine face = face();
            if (face == null || maxDrain <= 0) return FluidStack.EMPTY;
            for (int tank = 0; tank < face.tanks(); tank++) {
                Fluid fluid = face.fluid(tank);
                if (fluid == Fluids.EMPTY || !face.provides(tank, fluid)) continue;
                long moved = FluidStaging.extractNow(face, fluid, maxDrain, action.simulate());
                if (moved > 0L) return new FluidStack(fluid, FluidUnits.saturate(moved));
            }
            return FluidStack.EMPTY;
        }
    }

    private static final class ItemHandler implements IFluidHandlerItem {

        private final FluidContainer contract;
        private ItemStack container;

        ItemHandler(FluidContainer contract, ItemStack container) {
            this.contract = contract;
            this.container = container;
        }

        @Override
        public ItemStack getContainer() {
            return container;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (container.isEmpty() || !contract.handles(container)) return FluidStack.EMPTY;
            ItemStack one = container.copyWithCount(1);
            Fluid fluid = contract.fluid(one);
            long amount = contract.amount(one);
            return fluid == Fluids.EMPTY || amount <= 0L ? FluidStack.EMPTY : new FluidStack(fluid, FluidUnits.saturate(amount));
        }

        @Override
        public int getTankCapacity(int tank) {
            if (container.isEmpty() || !contract.handles(container)) return 0;
            return FluidUnits.saturate(contract.capacity(container.copyWithCount(1), Fluids.EMPTY));
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return !stack.isEmpty() && !container.isEmpty() && contract.handles(container)
                    && contract.capacity(container.copyWithCount(1), stack.getFluid()) > 0L;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || container.getCount() != 1 || !contract.handles(container)) return 0;
            FluidContainer.Moved moved = contract.fill(container.copyWithCount(1), resource.getFluid(), resource.getAmount());
            if (moved == null || moved.mb() <= 0L) return 0;
            if (action.execute()) container = moved.result();
            return FluidUnits.saturate(moved.mb());
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || container.getCount() != 1 || !contract.handles(container)) return FluidStack.EMPTY;
            FluidContainer.Moved moved = contract.drain(container.copyWithCount(1), resource.getFluid(), resource.getAmount());
            if (moved == null || moved.mb() <= 0L) return FluidStack.EMPTY;
            if (action.execute()) container = moved.result();
            return new FluidStack(resource.getFluid(), FluidUnits.saturate(moved.mb()));
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (container.isEmpty() || !contract.handles(container)) return FluidStack.EMPTY;
            Fluid fluid = contract.fluid(container.copyWithCount(1));
            if (fluid == Fluids.EMPTY) return FluidStack.EMPTY;
            return drain(new FluidStack(fluid, maxDrain), action);
        }
    }
}
*///?}
