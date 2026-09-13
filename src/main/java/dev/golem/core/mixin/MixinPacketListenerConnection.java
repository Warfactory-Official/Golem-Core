package dev.golem.core.mixin;

import dev.golem.core.net.ChannelAccess;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes the {@code Connection} a play listener holds.
 *
 * <p>The field moved up into {@code ServerCommonPacketListenerImpl} when that class was introduced,
 * and is private before then, so the target is switched rather than shadowed through the hierarchy —
 * a shadow of an inherited field is the kind of thing that validates on one node and not another. The
 * interface lands on whichever class actually declares the field, and the subclass a player holds is
 * an instance of it either way.
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
