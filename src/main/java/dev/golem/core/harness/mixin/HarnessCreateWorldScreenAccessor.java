package dev.golem.core.harness.mixin;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Harness: submit vanilla's world-creation screen without clicking its button. */
@Mixin(CreateWorldScreen.class)
public interface HarnessCreateWorldScreenAccessor {

    @Invoker("onCreate")
    void golem$onCreate();
}
