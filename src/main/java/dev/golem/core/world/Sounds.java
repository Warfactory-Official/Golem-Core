package dev.golem.core.world;

import dev.golem.core.Ids;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;

//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
 *///?}
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Sound registry lookup and public entity vibrations across the registry and game-event APIs.
 */
public final class Sounds {

    private Sounds() {
    }

    public static SoundEvent get(
            //? if >=26.1 {
            Identifier
                    //?} else {
                    /*ResourceLocation
                     *///?}
                    id) {
        //? if >=26.1 {
        return net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(id);
        //?} else {
        /*return net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(id);
         *///?}
    }

    /**
     * Public equipment noise: ENTITY_ACTION replaced the frequency-four ENTITY_ROAR event after 1.20.1.
     */
    public static void entityNoise(Entity source, double x, double y, double z, float volume) {
        if (volume > 0F && source.level() instanceof ServerLevel server) {
            server.gameEvent(
                    //? if >=1.21.1 {
                    GameEvent.ENTITY_ACTION,
                    //?} else {
                    /*GameEvent.ENTITY_ROAR,
                     *///?}
                    new Vec3(x, y, z), GameEvent.Context.of(source));
        }
    }

    public static SoundEvent minecraft(String path) {
        return get(Ids.minecraft(path));
    }

    /**
     * Observers hear the position; an emitting player hears the sound attached to its own entity.
     */
    public static void playFrom(Entity emitter, double x, double y, double z, SoundEvent event,
                                SoundSource source, float volume, float pitch) {
        if (!(emitter instanceof ServerPlayer player)) {
            emitter.level().playSound(null, x, y, z, event, source, volume, pitch);
            entityNoise(emitter, x, y, z, volume);
            return;
        }
        var level = (ServerLevel) player.level();
        Holder<SoundEvent> sound = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event);
        //? if neoforge {
        var played = net.neoforged.neoforge.event.EventHooks.onPlaySoundAtPosition(
                level, x, y, z, sound, source, volume, pitch);
        //?} elif forge {
        /*var played = net.minecraftforge.event.ForgeEventFactory.onPlaySoundAtPosition(
                level, x, y, z, sound, source, volume, pitch);
        *///?}
        //? if !fabric {
        if (played.isCanceled() || played.getSound() == null) return;
        sound = played.getSound();
        source = played.getSource();
        volume = played.getNewVolume();
        pitch = played.getNewPitch();
        //?}
        entityNoise(emitter, x, y, z, volume);
        long seed = level.getRandom().nextLong();
        level.getServer().getPlayerList().broadcast(player, x, y, z, sound.value().getRange(volume),
                level.dimension(), new ClientboundSoundPacket(sound, source, x, y, z, volume, pitch, seed));
        player.connection.send(new ClientboundSoundEntityPacket(sound, source, player, volume, pitch, seed));
    }
}
