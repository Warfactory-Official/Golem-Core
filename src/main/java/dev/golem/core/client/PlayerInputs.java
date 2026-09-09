package dev.golem.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Reads the local movement key state across the ClientInput/Input split. */
public final class PlayerInputs {

    private PlayerInputs() {}

    public record State(float forward, float strafe, boolean jump, boolean sneak, boolean sprint) {}

    public static State read(LocalPlayer player) {
        //? if >=26.1 {
        net.minecraft.world.entity.player.Input input = player.input.keyPresses;
        float forward = (input.forward() ? 1F : 0F) - (input.backward() ? 1F : 0F);
        float strafe = (input.left() ? 1F : 0F) - (input.right() ? 1F : 0F);
        return new State(forward, strafe, input.jump(), input.shift(), input.sprint());
        //?} else {
        /*net.minecraft.client.player.Input input = player.input;
        return new State(input.forwardImpulse, input.leftImpulse, input.jumping, input.shiftKeyDown,
                Minecraft.getInstance().options.keySprint.isDown());
        *///?}
    }
}
