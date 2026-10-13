package dev.golem.core.mixin;

import dev.golem.core.net.ChannelAccess;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes the {@code Connection} a play listener holds.
 *
 * <p>From 1.20.2 the field is declared in {@code ServerCommonPacketListenerImpl}; before that it is private in
 * {@code ServerGamePacketListenerImpl}. The mixin targets whichever class declares it, because shadowing an
 * inherited field can validate on one node and not another. The subclass a player holds is an instance of it
 * either way.
 */
//? if >=1.20.2 {
@Mixin(net.minecraft.server.network.ServerCommonPacketListenerImpl.class)
public abstract class MixinPacketListenerConnection implements ChannelAccess.HasConnection {

    @Shadow
    protected Connection connection;
//?} else {
/*@Mixin(net.minecraft.server.network.ServerGamePacketListenerImpl.class)
public abstract class MixinPacketListenerConnection implements ChannelAccess.HasConnection {

    @Shadow
    private Connection connection;
*///?}

    @Override
    public Connection golem$connection() {
        return connection;
    }
}
