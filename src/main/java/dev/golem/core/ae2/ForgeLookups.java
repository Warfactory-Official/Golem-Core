package dev.golem.core.ae2;

//? if ae2 && forge {
/*import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.storage.MEStorage;
import appeng.capabilities.Capabilities;
import dev.golem.core.Ids;
import dev.golem.core.autocraft.CraftingMachinePorts;
import dev.golem.core.storage.ItemStorePorts;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.jspecify.annotations.Nullable;

// Block entity capabilities, so a folded multiblock cell exposes nothing. Asked per query, never cached here.
final class ForgeLookups {

    private ForgeLookups() {}

    static void exposeMachines(CraftingMachinePorts ports, Predicate<Block> carriers) {
        attach("crafting_machine", carriers, (be, side) -> be.getLevel() instanceof ServerLevel server
                ? MachineAdapter.of(ports.at(server, be.getBlockPos(), be.getBlockState(), be, side)) : null,
                Capabilities.CRAFTING_MACHINE);
    }

    static boolean machineExposed(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && ICraftingMachine.of(level, pos, side, be) != null;
    }

    static void exposeStores(ItemStorePorts ports, Predicate<Block> carriers) {
        attach("item_store", carriers, (be, side) -> be.getLevel() instanceof ServerLevel server
                ? StoreAdapter.of(ports.at(server, be.getBlockPos(), be.getBlockState(), be, side)) : null,
                Capabilities.STORAGE);
    }

    static @Nullable MEStorage findStore(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        return be == null ? null : be.getCapability(Capabilities.STORAGE, side).resolve().orElse(null);
    }

    private interface Answer<T> {
        @Nullable T at(BlockEntity be, @Nullable Direction side);
    }

    private static <T> void attach(String name, Predicate<Block> carriers, Answer<T> answer, Capability<T> capability) {
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, (AttachCapabilitiesEvent<BlockEntity> event) -> {
            BlockEntity be = event.getObject();
            if (!carriers.test(be.getBlockState().getBlock())) return;
            event.addCapability(Ids.of("golemcore", name), new ICapabilityProvider() {
                @Override
                public <C> LazyOptional<C> getCapability(Capability<C> asked, @Nullable Direction side) {
                    if (asked != capability) return LazyOptional.empty();
                    T value = answer.at(be, side);
                    return value == null ? LazyOptional.empty() : LazyOptional.of(() -> value).cast();
                }
            });
        });
    }
}
*///?}
