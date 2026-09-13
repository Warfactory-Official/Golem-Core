package dev.golem.core.mixin;

import dev.golem.core.net.ChannelAccess;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes the Netty channel a connection owns, so a compiled packet can be written to several of them
 * without being re-encoded once per recipient. Vanilla keeps the field private on every supported
 * version and offers no accessor.
 */
@Mixin(Connection.class)
public abstract class MixinConnection implements ChannelAccess.HasChannel {

    @Shadow
    private Channel channel;

    @Override
    public Channel golem$channel() {
        return channel;
    }
}
