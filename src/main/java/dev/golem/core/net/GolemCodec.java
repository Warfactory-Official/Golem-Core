package dev.golem.core.net;

import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
//?}

/**
 * A payload's version-neutral wire format.
 *
 * <p>Consumers declare this once. Legacy transports use {@link #encode(Object, FriendlyByteBuf)}
 * directly; modern transports obtain their {@link StreamCodec} from the same definition.
 */
public final class GolemCodec<P extends GolemPayload> {

    private final Class<P> payloadType;
    private final
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            id;
    private final BiConsumer<P, FriendlyByteBuf> encoder;
    private final Function<FriendlyByteBuf, P> decoder;

    private GolemCodec(Class<P> payloadType,
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            id, BiConsumer<P, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, P> decoder) {
        this.payloadType = payloadType;
        this.id = id;
        this.encoder = encoder;
        this.decoder = decoder;
    }

    public static <P extends GolemPayload> GolemCodec<P> of(Class<P> payloadType,
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            id,
            BiConsumer<P, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, P> decoder) {
        return new GolemCodec<>(payloadType, id, encoder, decoder);
    }

    /**
     * Like {@link #of}, but the encoder and decoder take the registry-aware buffer, so a payload can share one write/read
     * method with block-entity sync ({@code serialize(RegistryFriendlyByteBuf)}) without naming the plain buffer type.
     * Where the registry-aware buffer does not exist (below 1.20.5) it is the plain buffer itself. Modern play transports
     * already hand a registry-aware buffer; any other buffer is wrapped over the same bytes (see
     * {@link GolemBuffers#registry}).
     */
    public static <P extends GolemPayload> GolemCodec<P> ofRegistry(Class<P> payloadType,
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            id,
            //? if >=1.20.5 {
            BiConsumer<P, net.minecraft.network.RegistryFriendlyByteBuf> encoder,
            Function<net.minecraft.network.RegistryFriendlyByteBuf, P> decoder) {
            //?} else {
            /*BiConsumer<P, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, P> decoder) {
            *///?}
        return new GolemCodec<>(payloadType, id, (payload, buffer) -> encoder.accept(payload, GolemBuffers.registry(buffer)),
                buffer -> decoder.apply(GolemBuffers.registry(buffer)));
    }

    /** Concrete payload class required by Forge 1.20.1's {@code SimpleChannel}. */
    public Class<P> payloadType() {
        return payloadType;
    }

    public
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            id() {
        return id;
    }

    public void encode(P payload, FriendlyByteBuf buffer) {
        encoder.accept(payload, buffer);
    }

    public P decode(FriendlyByteBuf buffer) {
        return decoder.apply(buffer);
    }

    //? if >=1.20.5 {
    /** The vanilla payload type paired with this codec's stable channel id. */
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<P> type() {
        return new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(id);
    }

    /** Adapts this codec to the vanilla payload API without a second serializer declaration. */
    public <B extends FriendlyByteBuf> StreamCodec<B, P> streamCodec() {
        return StreamCodec.of((buffer, payload) -> encode(payload, buffer), this::decode);
    }
    //?}
}
