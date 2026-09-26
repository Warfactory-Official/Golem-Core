package dev.golem.core.client;

import dev.golem.core.content.SyncedIndex;
import dev.golem.core.net.GolemNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client half of {@link SyncedIndex}: disconnects on a table mismatch. */
public final class SyncedIndexClient {

    private SyncedIndexClient() {}

    public static void register() {
        GolemNetwork.registerPlayToClient(SyncedIndex.Hashes.CODEC, (payload, context) -> {
            String mismatch = SyncedIndex.mismatch(payload.tables());
            if (mismatch == null) return;
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) connection.getConnection().disconnect(Component.literal(
                    "Content registry mismatch with server (" + mismatch + "): client and server need the same mods and scripts."));
        });
    }
}
