package dev.golem.core.net;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;

/**
 * The two hops from a player to the Netty channel underneath, neither of which vanilla exposes.
 *
 * <p>{@code Connection.channel} is private on every supported version, and the field holding the
 * {@code Connection} is private on 1.20.1 and protected afterwards, having moved up into
 * {@code ServerCommonPacketListenerImpl} when that class appeared. No loader offers a hook for either,
 * so the accelerated transport reaches them through mixins that implement these two interfaces onto
 * the vanilla classes. Nothing else in Golem needs them; the safe transport never asks.
 */
public final class ChannelAccess {

    private ChannelAccess() {}

    /** Implemented onto {@code Connection}. */
    public interface HasChannel {
        Channel golem$channel();
    }

    /** Implemented onto the play packet listener. */
    public interface HasConnection {
        Connection golem$connection();
    }
}
