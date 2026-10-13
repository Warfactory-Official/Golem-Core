package dev.golem.core.world;

import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;

/**
 * Music discs, declared the same way on every version.
 *
 * <p>1.21 moved a disc's song out of the item: the item only carries a {@code jukebox_playable}
 * reference to a data-driven {@code jukebox_song} entry, which holds the sound, the description,
 * the length and the comparator output. Before that the disc <em>was</em> the song: a
 * {@code RecordItem} constructed with all four. A caller declares both halves once: the item through
 * {@link #disc}, and the {@code data/<ns>/jukebox_song/<song>.json} file through
 * {@link dev.golem.core.dejson.JukeboxSongs}, which older versions never read.
 *
 * <p>The older disc resolves its sound lazily: Forge registers items before sound events.
 */
public final class Records {

    private Records() {}

    /**
     * A music disc item.
     *
     * @param properties    the item's properties (rarity, stack size: a disc stacks to one either way)
     * @param namespace     the song's namespace
     * @param song          the song's path, the {@code jukebox_song} entry name on 1.21+
     * @param sound         the event the disc plays (read on 1.20.1 only; 1.21+ reads the data file)
     * @param comparator    the jukebox comparator output while this disc plays
     * @param lengthSeconds how long the jukebox considers the disc playing
     */
    public static Item disc(Item.Properties properties, String namespace, String song,
                            Supplier<SoundEvent> sound, int comparator, int lengthSeconds) {
        //? if >=1.21 {
        return new Item(properties.stacksTo(1).jukeboxPlayable(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.JUKEBOX_SONG, dev.golem.core.Ids.of(namespace, song))));
        //?} else {
        /*return new LegacyDisc(comparator, sound, properties.stacksTo(1), lengthSeconds);
        *///?}
    }

    //? if <1.21 {
    /*/^* The pre-1.21 disc, which carries its own song. ^/
    private static final class LegacyDisc extends net.minecraft.world.item.RecordItem {

        private final Supplier<SoundEvent> sound;

        LegacyDisc(int comparator, Supplier<SoundEvent> sound, Item.Properties properties, int lengthSeconds) {
            // Forge's vanilla constructor looks the event up in the sound registry, which is still empty while
            // items register, so Forge takes its supplier constructor (which counts the length in ticks). Elsewhere
            // the placeholder event only fills vanilla's name table; getSound below is what the jukebox plays.
            //? if forge {
            /^super(comparator, sound, properties, lengthSeconds * 20);
            ^///?} else {
            super(comparator, SoundEvent.createVariableRangeEvent(dev.golem.core.Ids.of("golemcore", "disc")),
                    properties, lengthSeconds);
            //?}
            this.sound = sound;
        }

        @Override
        public SoundEvent getSound() {
            return sound.get();
        }
    }
    *///?}
}
