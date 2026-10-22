package dev.golem.core.net;

import dev.golem.core.GolemCore;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;

/**
 * Serialises a payload once and writes those same bytes to every recipient's channel.
 *
 * <p>A loader's transport encodes per recipient: a payload going to eight players is built eight times, and
 * each send wakes its event loop and flushes on its own. This builds the clientbound custom-payload packet's
 * bytes once (packet id, channel, body) and hands each channel a duplicate sharing the same memory. Writing a
 * raw buffer at the channel level is safe because the packet encoder only claims {@code Packet} instances and
 * passes anything else through, so length prefix, compression and encryption still run.
 *
 * <p>{@link #available()} is false when the packet id could not be probed or a player's channel cannot be
 * reached, and {@link GolemTransport} then falls back to the loader's own transport.
 */
public final class GolemWire {

    private static final ConcurrentHashMap<String, byte[]> CHANNEL_PREFIX = new ConcurrentHashMap<>();
    private static final Map<ServerPlayer, List<ByteBuf>> PENDING = new IdentityHashMap<>();

    private static volatile byte[] packetIdBytes;
    private static boolean probed;

    private GolemWire() {}

    /**
     * Whether the accelerated path can be used at all. Probes on the first ask and remembers the
     * answer, including a failure. The id is fixed by the protocol, not by anything a server does,
     * so there is no moment this has to wait for.
     */
    public static synchronized boolean available() {
        if (packetIdBytes != null) return true;
        if (probed) return false;
        probed = true;
        if (!mixinsApplied()) {
            GolemCore.LOG.warn("Channel accessors are not present; staying on the safe transport");
            return false;
        }
        Integer id = probePacketId();
        if (id == null) {
            GolemCore.LOG.warn("Could not read the custom-payload packet id; staying on the safe transport");
            return false;
        }
        packetIdBytes = varInt(id);
        GolemCore.LOG.info("Accelerated payload transport available (custom-payload packet id {})", id);
        return true;
    }

    /**
     * Whether both channel accessors actually landed on their vanilla targets.
     *
     * <p>Mixins apply when their target class loads, so a quiet startup does not show these two applied: the
     * listener class may not have loaded yet. Asking the class forces the load and answers the question.
     */
    public static boolean mixinsApplied() {
        return ChannelAccess.HasChannel.class.isAssignableFrom(Connection.class)
                && ChannelAccess.HasConnection.class.isAssignableFrom(
                        ServerGamePacketListenerImpl.class);
    }

    /** The clientbound custom-payload packet's network id, or -1 if it could not be read. */
    public static int packetId() {
        if (!available()) return -1;
        int value = 0;
        int shift = 0;
        for (byte piece : packetIdBytes) {
            value |= (piece & 0x7F) << shift;
            shift += 7;
        }
        return value;
    }

    /** Drops everything queued and the probed id. For a server stopping, and for tests. */
    public static synchronized void forget() {
        discardPending();
        packetIdBytes = null;
        probed = false;
        CHANNEL_PREFIX.clear();
    }

    //? if >=26.1 {
    private static Integer probePacketId() {
        int[] found = {-1};
        net.minecraft.network.protocol.game.GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets(
                (type, networkId) -> {
                    if (type == net.minecraft.network.protocol.common.CommonPacketTypes.CLIENTBOUND_CUSTOM_PAYLOAD) {
                        found[0] = networkId;
                    }
                });
        return found[0] < 0 ? null : found[0];
    }
    //?} elif >=1.20.5 {
    /*private static Integer probePacketId() {
        int[] found = {-1};
        net.minecraft.network.protocol.game.GameProtocols.CLIENTBOUND_TEMPLATE.listPackets(
                (type, networkId) -> {
                    if (type == net.minecraft.network.protocol.common.CommonPacketTypes.CLIENTBOUND_CUSTOM_PAYLOAD) {
                        found[0] = networkId;
                    }
                });
        return found[0] < 0 ? null : found[0];
    }
    *///?} else {
    /*private static Integer probePacketId() {
        ByteBuf empty = Unpooled.buffer(0);
        try {
            net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket probe =
                    new net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket(
                            dev.golem.core.Ids.of(GolemCore.MOD_ID, "probe"), new FriendlyByteBuf(empty));
            int id = net.minecraft.network.ConnectionProtocol.PLAY.getPacketId(
                    net.minecraft.network.protocol.PacketFlow.CLIENTBOUND, probe);
            return id < 0 ? null : id;
        } finally {
            empty.release();
        }
    }
    *///?}

    /**
     * Compiles one payload into a wire buffer the caller owns. Returns null when acceleration is not
     * available, which is the caller's signal to use the loader's transport instead.
     */
    public static <P extends GolemPayload> ByteBuf compile(GolemCodec<P> codec, P payload, Level level) {
        if (!available()) return null;
        byte[] packetId = packetIdBytes;
        ByteBuf wire = PooledByteBufAllocator.DEFAULT.directBuffer();
        try {
            wire.writeBytes(packetId);
            wire.writeBytes(channelPrefix(codec));
            codec.encode(payload, GolemBuffers.wrap(wire, level));
            return wire;
        } catch (Throwable failed) {
            wire.release();
            throw failed;
        }
    }

    /**
     * Writes one compiled buffer to every recipient now, and consumes the caller's reference. False
     * means nothing was written and the caller should send by the safe path. The check covers every
     * recipient before the first write, so a fallback cannot double-send to some of them.
     */
    public static boolean sendNow(ByteBuf wire, Collection<ServerPlayer> recipients) {
        try {
            List<Channel> channels = channelsOf(recipients);
            if (channels == null) return false;
            for (Channel channel : channels) channel.write(wire.retainedDuplicate(), channel.voidPromise());
            for (Channel channel : channels) channel.flush();
            return true;
        } finally {
            wire.release();
        }
    }

    /**
     * Queues one compiled buffer for each recipient, to go out when the tick ends. Consumes the
     * caller's reference. Only for traffic that may wait a tick (a heartbeat, or a replay to someone
     * who has just come into view).
     */
    public static boolean sendBatched(ByteBuf wire, Collection<ServerPlayer> recipients) {
        try {
            if (channelsOf(recipients) == null) return false;
            for (ServerPlayer player : recipients) {
                PENDING.computeIfAbsent(player, p -> new ArrayList<>(1)).add(wire.retainedDuplicate());
            }
            return true;
        } finally {
            wire.release();
        }
    }

    /**
     * One task per recipient per tick, each ending in a single flush. Without the batch a player
     * watching twenty machines is woken twenty times and flushed twenty times in the same tick.
     */
    public static void flushPending() {
        if (PENDING.isEmpty()) return;
        try {
            for (Map.Entry<ServerPlayer, List<ByteBuf>> queued : PENDING.entrySet()) {
                Channel channel = channelOf(queued.getKey());
                List<ByteBuf> buffers = queued.getValue();
                if (channel == null || !channel.isActive()) {
                    for (ByteBuf buffer : buffers) buffer.release();
                    continue;
                }
                channel.eventLoop().execute(() -> {
                    for (ByteBuf buffer : buffers) channel.write(buffer, channel.voidPromise());
                    channel.flush();
                });
            }
        } finally {
            PENDING.clear();
        }
    }

    private static void discardPending() {
        for (List<ByteBuf> buffers : PENDING.values()) {
            for (ByteBuf buffer : buffers) buffer.release();
        }
        PENDING.clear();
    }

    /** Null when any recipient's channel cannot be reached, so the caller can fall back whole. */
    private static List<Channel> channelsOf(Collection<ServerPlayer> recipients) {
        if (recipients.isEmpty()) return null;
        List<Channel> channels = new ArrayList<>(recipients.size());
        for (ServerPlayer player : recipients) {
            Channel channel = channelOf(player);
            if (channel == null || !channel.isActive()) return null;
            channels.add(channel);
        }
        return channels;
    }

    /** The Netty channel behind a player, or null if the two mixin hops did not land. */
    public static Channel channelOf(ServerPlayer player) {
        if (player.connection == null) return null;
        if (!(player.connection instanceof ChannelAccess.HasConnection holder)) return null;
        Connection connection = holder.golem$connection();
        if (!(connection instanceof ChannelAccess.HasChannel owner)) return null;
        Channel channel = owner.golem$channel();
        // A raw buffer is only a packet to a pipeline that frames and decodes bytes. The integrated
        // server's in-memory (LocalChannel) connection hands Packet objects straight across with no
        // encoder, so a compiled buffer written there is silently discarded by the client's packet
        // handler: every block-entity sync on a 1.20.1 singleplayer world was lost this way. Such
        // channels take the loader's transport instead.
        if (channel == null || channel instanceof io.netty.channel.local.LocalChannel) return null;
        return channel;
    }

    /**
     * The bytes between the packet id and the body: the channel this payload travels on, plus
     * whatever else the loader's own framing puts there. Computed once per channel.
     */
    private static <P extends GolemPayload> byte[] channelPrefix(GolemCodec<P> codec) {
        return CHANNEL_PREFIX.computeIfAbsent(codec.id().toString(), ignored -> buildChannelPrefix(codec));
    }

    private static <P extends GolemPayload> byte[] buildChannelPrefix(GolemCodec<P> codec) {
        ByteBuf scratch = Unpooled.buffer();
        try {
            FriendlyByteBuf writer = new FriendlyByteBuf(scratch);
            //? if >=26.1 {
            writer.writeIdentifier(codec.id());
            //?} elif forge && <1.20.5 {
            /*writer.writeResourceLocation(GolemNetwork.forgeChannel());
            scratch.writeByte(GolemNetwork.forgeDiscriminator(codec) & 0xff);
            *///?} else {
            /*writer.writeResourceLocation(codec.id());
            *///?}
            byte[] prefix = new byte[scratch.readableBytes()];
            scratch.readBytes(prefix);
            return prefix;
        } finally {
            scratch.release();
        }
    }

    private static byte[] varInt(int value) {
        ByteBuf scratch = Unpooled.buffer(5);
        try {
            int remaining = value;
            do {
                byte piece = (byte) (remaining & 0x7F);
                remaining >>>= 7;
                if (remaining != 0) piece |= (byte) 0x80;
                scratch.writeByte(piece);
            } while (remaining != 0);
            byte[] bytes = new byte[scratch.readableBytes()];
            scratch.readBytes(bytes);
            return bytes;
        } finally {
            scratch.release();
        }
    }
}
