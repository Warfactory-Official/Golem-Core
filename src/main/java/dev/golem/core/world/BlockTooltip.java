package dev.golem.core.world;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A block that writes the first lines of its item's tooltip, ahead of the item's own lines. Read by
 * {@link CompatBlockItem}, so a plain registered block item needs no subclass for it.
 */
public interface BlockTooltip {

    void appendBlockTooltip(ItemStack stack, List<Component> tooltip, TooltipFlag flag);
}
