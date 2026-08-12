package dev.golem.core.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sending side of custom packets.
 *
 * <p>Scope note, because it is narrower than it looks. {@code CustomPacketPayload},
 * {@code StreamCodec} and {@code ByteBufCodecs} are identical on 1.21.1 and 26.1 — payload
 * <i>definitions</i> need no abstraction between those two, only the {@code Identifier}/
 * {@code ResourceLocation} rename that {@code Ids} already covers. What genuinely differs is:
 *
 * <ul>
 *   <li><b>Per loader:</b> how you send (Fabric {@code ServerPlayNetworking} vs NeoForge
 *       {@code PacketDistributor}) and how you enumerate players tracking a position.</li>
 *   <li><b>Pre-1.20.5:</b> the payload API does not exist at all. 1.20.1 needs a real shim over Forge's
 *       {@code SimpleChannel} / Fabric's raw {@code FriendlyByteBuf} channels, which is Phase 2 work
 *       and is deliberately not attempted here — there is no 1.20.1 node yet to verify it against, so
 *       writing it now would be unverifiable guesswork.</li>
 * </ul>
 *
 * <p>Registration is intentionally absent: it is event-timed and loader-shaped (Fabric's
 * {@code PayloadTypeRegistry} at init, NeoForge's {@code RegisterPayloadHandlersEvent}), so it stays in
 * each loader's bootstrap where the timing is explicit.
 */
public interface PacketRouter {

    /** Sends to one player. */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Sends to every player tracking the chunk containing {@code pos} — the block-entity sync path. */
    void sendToTracking(ServerLevel level, BlockPos pos, CustomPacketPayload payload);

    /** Sends to the server. Client side only. */
    void sendToServer(CustomPacketPayload payload);
}
