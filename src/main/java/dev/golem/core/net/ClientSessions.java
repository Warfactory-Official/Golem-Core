package dev.golem.core.net;

import dev.golem.core.hook.Hook;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import org.jspecify.annotations.Nullable;

/** Client metadata ownership, identified by the actual connection and its configuration generation. */
public final class ClientSessions {

    public static final Hook<Consumer<Session>> START = Hook.notifying();
    public static final Hook<Consumer<Session>> END = Hook.notifying();
    private static volatile @Nullable Session current;

    private ClientSessions() {}

    public static final class Session {
        private final Connection connection;
        private volatile boolean open = true;
        private volatile boolean configuration;
        private volatile Object playOwner;

        private Session(Connection connection, boolean configuration) {
            this.connection = connection;
            this.configuration = configuration;
        }

        public Connection connection() { return connection; }
        public boolean configuration() { return configuration; }
        public boolean active() { return open && current == this && connection.isConnected(); }
    }

    public static @Nullable Session current() { return current; }

    public static @Nullable Session current(Connection connection) {
        var session = current;
        return session != null && session.connection == connection && session.active() ? session : null;
    }

    /** The listener must still be installed on its connection; queued packets from a replaced phase are stale. */
    public static @Nullable Session forListener(Object listener) {
        var connection = ((ChannelAccess.HasConnection) listener).golem$connection();
        return connection.getPacketListener() == listener ? current(connection) : null;
    }

    public static void bindPlayOwner(Object owner, Object listener) {
        var session = forListener(listener);
        if (session != null) session.playOwner = owner;
    }

    public static @Nullable Session forPlayOwner(Object owner) {
        var session = current;
        return session != null && session.playOwner == owner && !session.configuration && session.active()
                ? session : null;
    }

    public static synchronized void configure(Connection connection) {
        open(connection, true);
    }

    public static synchronized void play(Connection connection) {
        var session = current;
        if (session == null || session.connection != connection || !session.open) open(connection, false);
        else session.configuration = false;
    }

    private static void open(Connection connection, boolean configuration) {
        var previous = current;
        if (previous != null) close(previous);
        var session = new Session(connection, configuration);
        current = session;
        for (var listener : START) listener.accept(session);
    }

    public static synchronized void disconnected(Connection connection) {
        var session = current;
        if (session != null && session.connection == connection) close(session);
    }

    private static void close(Session session) {
        session.open = false;
        if (current == session) current = null;
        for (var listener : END) listener.accept(session);
    }
}
