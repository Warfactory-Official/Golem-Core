package dev.golem.probe;

import dev.golem.core.info.BlockInfo;
import dev.golem.core.info.Gauge;
import dev.golem.core.viewer.Role;
import dev.golem.core.viewer.SlotLayout;
import dev.golem.core.viewer.ViewerCategory;
import dev.golem.core.viewer.ViewerPlugin;
import dev.golem.core.viewer.ViewerType;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * Recipe-viewer and block-info content for the viewer adapters: one category ("Probe fuel", paper into a smoker) that
 * JEI and REI list from {@code ProbeJeiPlugin} and {@code ProbeReiPlugin}, and a gauge Jade shows on every furnace.
 */
public final class ProbeViewer implements ViewerPlugin {

    public static final ProbeViewer INSTANCE = new ProbeViewer();
    public static final ViewerType<Fuel> FUEL = ViewerType.create(ProbeEntry.MOD_ID, "fuel", Fuel.class);

    public record Fuel(ItemStack fuel, ItemStack furnace) {}

    private ProbeViewer() {}

    public static void declareInfo() {
        BlockInfo.gauges(AbstractFurnaceBlockEntity.class, (furnace, out) ->
                out.accept(Gauge.of("Probe", 1, 2, "", 0xFFFF0000, 0xFF00FF00)));
    }

    @Override
    public void categories(Categories registration) {
        registration.add(new ViewerCategory<>(FUEL, Component.literal("Probe fuel"), new ItemStack(Items.PAPER), 60, 20) {
            @Override
            public void setRecipe(SlotLayout builder, Fuel recipe) {
                builder.addSlot(Role.INPUT, 1, 1).addItemStack(recipe.fuel()).setStandardSlotBackground();
                builder.addSlot(Role.CRAFTING_STATION, 41, 1).addItemStack(recipe.furnace()).setStandardSlotBackground();
            }
        });
    }

    @Override
    public void recipes(Recipes registration) {
        registration.add(FUEL, List.of(new Fuel(new ItemStack(Items.PAPER), new ItemStack(Items.SMOKER))));
    }
}
