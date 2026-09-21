package dev.golem.core.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Harness: synthetic keyboard events through the vanilla handler. */
@Mixin(KeyboardHandler.class)
public interface HarnessKeyboardHandlerAccessor {

    //? if >=26.1 {
    @Invoker("keyPress")
    void golem$keyPress(long window, int action, net.minecraft.client.input.KeyEvent event);

    @Invoker("charTyped")
    void golem$charTyped(long window, net.minecraft.client.input.CharacterEvent event);
    //?} else {
    /*@Invoker("charTyped")
    void golem$charTyped(long window, int codepoint, int modifiers);
    *///?}
}
