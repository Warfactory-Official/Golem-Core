package dev.golem.core.net;

//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
//? if >=1.20.5 {
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/** A consumer-defined play payload with a stable channel id. */
public interface GolemPayload
        //? if >=1.20.5 {
        extends CustomPacketPayload
        //?}
{

    /** The channel used by this payload. Keep this value stable after release. */
    //? if >=26.1 {
    Identifier id();
    //?} else {
    /*ResourceLocation id();
    *///?}

    //? if >=1.20.5 {
    @Override
    default Type<? extends CustomPacketPayload> type() {
        return new Type<>(id());
    }
    //?}
}
