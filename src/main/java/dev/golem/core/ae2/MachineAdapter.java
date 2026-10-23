package dev.golem.core.ae2;

//? if ae2 {
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import dev.golem.core.autocraft.CraftingMachine;
import dev.golem.core.autocraft.CraftingPlan;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

// AE2 counts fluids in its own units, 81,000 per bucket on Fabric.
final class MachineAdapter implements ICraftingMachine {

    private final CraftingMachine machine;

    MachineAdapter(CraftingMachine machine) {
        this.machine = machine;
    }

    static @Nullable ICraftingMachine of(@Nullable CraftingMachine machine) {
        return machine == null ? null : new MachineAdapter(machine);
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        ItemStack icon = machine.icon();
        return new PatternContainerGroup(AEItemKey.of(icon), icon.getHoverName(), List.of());
    }

    @Override
    public boolean acceptsPlans() {
        return machine.acceptsPlans();
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, Direction ejectionDirection) {
        CraftingPlan plan = plan(pattern, inputs);
        return plan != null && machine.push(plan);
    }

    /** Null when an input is neither an item nor a fluid. */
    static @Nullable CraftingPlan plan(IPatternDetails pattern, KeyCounter[] inputs) {
        List<ItemStack> items = new ArrayList<>();
        List<CraftingPlan.FluidAmount> fluids = new ArrayList<>();
        for (KeyCounter counter : inputs) {
            for (Object2LongMap.Entry<AEKey> entry : counter) {
                long amount = entry.getLongValue();
                if (entry.getKey() instanceof AEItemKey item) {
                    items.add(item.toStack((int) Math.min(amount, Integer.MAX_VALUE)));
                } else if (entry.getKey() instanceof AEFluidKey fluid) {
                    fluids.add(new CraftingPlan.FluidAmount(fluid.getFluid(),
                            (int) Math.min(amount * 1000L / AEFluidKey.AMOUNT_BUCKET, Integer.MAX_VALUE)));
                } else {
                    return null;
                }
            }
        }
        GenericStack primary = pattern.getPrimaryOutput();
        AEKey what = primary == null ? null : primary.what();
        ItemStack primaryItem = what instanceof AEItemKey item ? item.toStack() : null;
        Fluid primaryFluid = what instanceof AEFluidKey fluid ? fluid.getFluid() : null;
        return new CraftingPlan(items, fluids, primaryItem, primaryFluid);
    }
}
//?}
