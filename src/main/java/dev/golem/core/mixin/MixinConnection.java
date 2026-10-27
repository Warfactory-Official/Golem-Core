package dev.golem.core.mixin;

import dev.golem.core.net.ClientSessions;
import io.netty.channel.ChannelHandlerContext;

import dev.golem.core.net.ChannelAccess;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Exposes the Netty channel a connection owns, so a compiled packet can be written to several of them
 * without being re-encoded once per recipient. Vanilla keeps the field private on every supported
 * version and offers no accessor.
 */
@Mixin(Connection.class)
public abstract class MixinConnection implements ChannelAccess.HasChannel {

    @Shadow
    private Channel channel;

    // Ends the client session for the connection that closed. Loader events cannot: NeoForge and Forge LoggingOut and
    // Fabric's play DISCONNECT miss a drop during configuration, and NeoForge has no configuration disconnect event.
    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void golem$connectionClosed(ChannelHandlerContext context, CallbackInfo ci) {
        ClientSessions.disconnected((Connection) (Object) this);
    }

    @Override
    public Channel golem$channel() {
        return channel;
    }
}
