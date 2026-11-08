package dev.golem.core.viewer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * Items that stand for a fluid in a mod's recipe slots, as 1.7 NEI's fluid icons did. Every viewer also files such a
 * slot under the fluid itself, so looking up the fluid finds the recipe. EMI shows the fluid, drawn as the icon, in
 * the slot and in its list; JEI and REI keep the icon and add the fluid as an invisible ingredient beside it.
 */
public final class FluidIcons {

    /** What an icon stands for; {@code milliBuckets} is 0 when it carries no amount. */
    public record Amount(Fluid fluid, long milliBuckets) {}

    @FunctionalInterface
    public interface Declaration {
        /** The fluid an icon stack stands for, or null when it stands for none. */
        @Nullable Amount fluidOf(ItemStack icon);
    }

    private static final Map<Item, Declaration> DECLARED = new ConcurrentHashMap<>();

    private FluidIcons() {}

    public static void declare(Item icon, Declaration declaration) {
        DECLARED.put(icon, declaration);
    }

    public static @Nullable Amount fluidOf(ItemStack stack) {
        Declaration declaration = stack.isEmpty() ? null : DECLARED.get(stack.getItem());
        Amount amount = declaration == null ? null : declaration.fluidOf(stack);
        return amount == null || amount.fluid() == Fluids.EMPTY ? null : amount;
    }
}
