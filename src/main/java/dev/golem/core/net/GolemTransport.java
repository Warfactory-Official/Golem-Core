package dev.golem.core.net;

import io.netty.buffer.ByteBuf;
import java.util.Collection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Chooses how a payload reaches its recipients.
 *
 * <p>Two backends answer the same question and must produce the same bytes. {@link Backend#SAFE} is
 * the loader's own transport, encoding once per recipient through the API that loader publishes.
 * {@link Backend#ACCELERATED} compiles the packet once and writes those bytes to every recipient's
 * channel, reaching the channel through mixins because no loader exposes it. Accelerated is the
 * default, and it is the one that can be wrong in ways a compile will not catch, so the tests hold
 * the two against each other byte for byte rather than testing each alone.
 *
 * <p>Selection degrades rather than fails. Asking for acceleration where the packet id could not be
 * read, or for a recipient whose channel cannot be reached, sends by the safe path instead.
 */
public final class GolemTransport {

    public enum Backend {
        ACCELERATED,
        SAFE
    }

    private static Backend requested = Backend.ACCELERATED;

    private GolemTransport() {}

    /** What has been asked for, which is not always what a given send can do. */
    public static Backend requested() {
        return requested;
    }

    public static void requested(Backend backend) {
        requested = backend;
    }

    /** What a send would actually use right now. */
    public static Backend effective() {
        return requested == Backend.ACCELERATED && GolemWire.available()
                ? Backend.ACCELERATED : Backend.SAFE;
    }

    /**
     * Sends one payload to every recipient. When {@code batched}, the accelerated path defers the
     * write to the end of the tick so a player watching many senders is woken once; the safe path has
     * no such concept and sends immediately either way.
     */
    public static <P extends GolemPayload> void send(Collection<ServerPlayer> recipients,
            GolemCodec<P> codec, P payload, Level level, boolean batched) {
        if (recipients.isEmpty()) return;
        if (effective() == Backend.ACCELERATED && sendAccelerated(recipients, codec, payload, level, batched)) {
            return;
        }
        for (ServerPlayer player : recipients) GolemNetwork.sendToPlayer(player, codec, payload);
    }

    private static <P extends GolemPayload> boolean sendAccelerated(Collection<ServerPlayer> recipients,
            GolemCodec<P> codec, P payload, Level level, boolean batched) {
        ByteBuf wire = GolemWire.compile(codec, payload, level);
        if (wire == null) return false;
        if (batched) return GolemWire.sendBatched(wire, recipients);
        return GolemWire.sendNow(wire, recipients);
    }

    /** Called at the end of the server tick, after anything that may have queued a batched send. */
    public static void endTick() {
        GolemWire.flushPending();
    }
}
