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

    /**
     * The registry-aware view of a packet buffer: the buffer itself when it already is one (every modern play transport),
     * else a registry-aware buffer over the same bytes, backed by the built-in registries. Below 1.20.5 the plain buffer
     * is returned unchanged.
     */
    public static
            //? if >=1.20.5 {
            RegistryFriendlyByteBuf
            //?} else {
            /*FriendlyByteBuf
            *///?}
            registry(net.minecraft.network.FriendlyByteBuf buffer) {
        //? if >=1.20.5 {
        if (buffer instanceof RegistryFriendlyByteBuf registryBuffer) return registryBuffer;
        return new RegistryFriendlyByteBuf(buffer, net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        //?} else {
        /*return buffer;
        *///?}
    }

    /** Writes chat text in the native format for the target version. */
    public static void writeComponent(
            //? if >=1.20.5 {
            RegistryFriendlyByteBuf
            //?} else {
            /*FriendlyByteBuf
            *///?}
            buffer, net.minecraft.network.chat.Component text) {
        //? if >=1.20.5 {
        net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, text);
        //?} else {
        /*buffer.writeComponent(text);
        *///?}
    }

    /** Reads chat text written by {@link #writeComponent}. */
    public static net.minecraft.network.chat.Component readComponent(
            //? if >=1.20.5 {
            RegistryFriendlyByteBuf
            //?} else {
            /*FriendlyByteBuf
            *///?}
            buffer) {
        //? if >=1.20.5 {
        return net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
        //?} else {
        /*return buffer.readComponent();
        *///?}
    }
}
