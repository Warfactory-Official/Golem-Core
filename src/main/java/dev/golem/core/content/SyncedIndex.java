package dev.golem.core.content;

import dev.golem.core.Ids;
import dev.golem.core.net.GolemCodec;
import dev.golem.core.net.GolemNetwork;
import dev.golem.core.net.GolemPayload;
import dev.golem.core.platform.Lifecycle;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Session-local int indexes (registration order) that network buffers carry instead of ids. Saves store ids; an index
 * is only valid while client and server registered the same ids in the same order. On join the server sends every
 * table's hash; a client whose table differs disconnects, naming the table.
 */
public final class SyncedIndex {

    private SyncedIndex() {}

    private static final Map<String, Supplier<List<String>>> TABLES = new LinkedHashMap<>();

    public record Hashes(Map<String, Long> tables) implements GolemPayload {
        public static final GolemCodec<Hashes> CODEC = GolemCodec.of(Hashes.class, Ids.of("golemcore", "synced_index"),
                (payload, buffer) -> {
                    buffer.writeVarInt(payload.tables.size());
                    payload.tables.forEach((name, hash) -> {
                        buffer.writeUtf(name);
                        buffer.writeLong(hash);
                    });
                },
                buffer -> {
                    int size = buffer.readVarInt();
                    Map<String, Long> tables = new LinkedHashMap<>();
                    for (int i = 0; i < size; i++) tables.put(buffer.readUtf(), buffer.readLong());
                    return new Hashes(tables);
                });

        @Override
        public
        //? if >=26.1 {
        net.minecraft.resources.Identifier
        //?} else {
        /*net.minecraft.resources.ResourceLocation
        *///?}
        id() {
            return CODEC.id();
        }
    }

    /** {@code ids}: the table in index order, read at every join. */
    public static synchronized void register(String name, Supplier<List<String>> ids) {
        if (TABLES.putIfAbsent(name, ids) != null) throw new IllegalStateException("Synced index registered twice: " + name);
    }

    public static synchronized Map<String, Long> hashes() {
        Map<String, Long> hashes = new LinkedHashMap<>();
        TABLES.forEach((name, ids) -> hashes.put(name, hash(ids.get())));
        return hashes;
    }

    /** First table whose local hash differs from {@code remote}, or null. */
    public static String mismatch(Map<String, Long> remote) {
        Map<String, Long> local = hashes();
        if (!local.keySet().equals(remote.keySet())) return "table set " + remote.keySet() + " vs " + local.keySet();
        for (var entry : remote.entrySet()) {
            if (!entry.getValue().equals(local.get(entry.getKey()))) return entry.getKey();
        }
        return null;
    }

    /** Golem Core bootstrap, before payload registrations flush. */
    public static void declare() {
        GolemNetwork.declarePlayToClient(Hashes.CODEC);
        Lifecycle.onPlayerJoin(player -> {
            if (GolemNetwork.canSendToPlayer(player, Hashes.CODEC)) {
                GolemNetwork.sendToPlayer(player, Hashes.CODEC, new Hashes(hashes()));
            }
        });
    }

    static long hash(List<String> ids) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String id : ids) {
                digest.update(id.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            byte[] bytes = digest.digest();
            long hash = 0;
            for (int i = 0; i < 8; i++) hash = hash << 8 | (bytes[i] & 0xFF);
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
