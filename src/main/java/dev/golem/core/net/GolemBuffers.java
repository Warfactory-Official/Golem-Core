package dev.golem.core.net;

import io.netty.buffer.ByteBuf;
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
//?} else {
/*import net.minecraft.network.FriendlyByteBuf;
*///?}
import net.minecraft.world.level.Level;

/** Creates registry-aware packet buffers where that concept exists. */
public final class GolemBuffers {

    private GolemBuffers() {}

    public static
            //? if >=1.20.5 {
            RegistryFriendlyByteBuf
            //?} else {
            /*FriendlyByteBuf
            *///?}
            wrap(ByteBuf buffer, Level level) {
        //? if >=1.20.5 {
        return new RegistryFriendlyByteBuf(buffer, level.registryAccess());
        //?} else {
        /*return new FriendlyByteBuf(buffer);
        *///?}
    }
}
