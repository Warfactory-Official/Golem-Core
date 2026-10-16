package dev.golem.core.harness.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Harness: container-screen layout for slot hit positions. */
@Mixin(AbstractContainerScreen.class)
public interface HarnessContainerScreenAccessor {

    @Accessor("leftPos")
    int golem$leftPos();

    @Accessor("topPos")
    int golem$topPos();

    @Accessor("imageWidth")
    int golem$imageWidth();

    @Accessor("imageHeight")
    int golem$imageHeight();

    @Accessor("hoveredSlot")
    Slot golem$hoveredSlot();
}
