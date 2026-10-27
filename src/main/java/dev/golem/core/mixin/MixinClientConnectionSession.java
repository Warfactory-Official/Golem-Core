package dev.golem.core.mixin;

//? if >=1.21 {
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
//?}
//? if >=1.21 {
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
//?}
import net.minecraft.client.multiplayer.ClientPacketListener;

import dev.golem.core.net.ChannelAccess;
import dev.golem.core.net.ClientSessions;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.20.2 {
/**
 * Vanilla has no metadata-session callback; loader play callbacks follow configuration.
 * Widening cannot add constructor notification or a common listener contract.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class MixinClientConnectionSession implements ChannelAccess.HasConnection {
    @Shadow protected Connection connection;
//?} else {
/*@Mixin(ClientPacketListener.class)
public abstract class MixinClientConnectionSession implements ChannelAccess.HasConnection {
    @Shadow private Connection connection;
*///?}

    @Override
    public Connection golem$connection() { return connection; }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void golem$metadataSession(CallbackInfo ci) {
        //? if >=1.20.5 {
        if ((Object) this instanceof ClientConfigurationPacketListenerImpl) {
            ClientSessions.configure(connection);
            return;
        }
        //?}
        ClientSessions.play(connection);
    }
}
