package dev.golem.core.harness.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Harness: synthetic mouse events through the vanilla handler. */
@Mixin(MouseHandler.class)
public interface HarnessMouseHandlerAccessor {

    //? if >=1.21.11 {
    @Invoker("onButton")
    void golem$onButton(long window, net.minecraft.client.input.MouseButtonInfo button, int action);
    //?} else {
    /*@Invoker("onPress")
    void golem$onPress(long window, int button, int action, int modifiers);
    *///?}

    //? if >=26.3 {
    /*@Invoker("onMove")
    void golem$onMove(long window, double x, double y, double dx, double dy);
    *///?} else {
    @Invoker("onMove")
    void golem$onMove(long window, double x, double y);
    //?}

    @Invoker("onScroll")
    void golem$onScroll(long window, double xOffset, double yOffset);
}
