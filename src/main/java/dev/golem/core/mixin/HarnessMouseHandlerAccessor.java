package dev.golem.core.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Harness: synthetic mouse events through the vanilla handler. */
@Mixin(MouseHandler.class)
public interface HarnessMouseHandlerAccessor {

    //? if >=26.1 {
    @Invoker("onButton")
    void golem$onButton(long window, net.minecraft.client.input.MouseButtonInfo button, int action);
    //?} else {
    /*@Invoker("onPress")
    void golem$onPress(long window, int button, int action, int modifiers);
    *///?}

    @Invoker("onMove")
    void golem$onMove(long window, double x, double y);

    @Invoker("onScroll")
    void golem$onScroll(long window, double xOffset, double yOffset);
}
