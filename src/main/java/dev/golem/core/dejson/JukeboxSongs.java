package dev.golem.core.dejson;

import com.google.gson.JsonObject;

/**
 * The data half of a music disc: {@code data/<ns>/jukebox_song/<song>.json}.
 *
 * <p>1.21 reads the song from this file and the item only points at it (see
 * {@code dev.golem.core.world.Records}). Versions before 1.21 have no such registry and never open
 * the directory, so declaring it unconditionally is harmless there.
 */
public final class JukeboxSongs {

    private JukeboxSongs() {}

    /**
     * @param descriptionKey translation key of the line under the disc's name ("Artist - Title")
     */
    public static JsonObject declare(String namespace, String song, String soundEvent, String descriptionKey,
                                     float lengthSeconds, int comparator) {
        Models.requireId(soundEvent, "sound event");
        if (lengthSeconds <= 0F) throw new DejsonException(song + " has no length");
        if (comparator < 0 || comparator > 15) throw new DejsonException(song + " comparator " + comparator);
        JsonObject description = new JsonObject();
        description.addProperty("translate", descriptionKey);
        JsonObject json = new JsonObject();
        json.addProperty("comparator_output", comparator);
        json.add("description", description);
        json.addProperty("length_in_seconds", lengthSeconds);
        json.addProperty("sound_event", soundEvent);
        return Data.declare("data/" + namespace + "/jukebox_song/" + song + ".json", json);
    }

    /**
     * Lists the disc items in {@code #minecraft:music_discs}. Before 1.21 a jukebox only keeps a disc carrying
     * that tag ({@code JukeboxBlockEntity.setFirstItem}); the record item still consumes an untagged disc, so a
     * missing entry silently eats it. 1.21+ gates on the {@code jukebox_playable} component instead and the extra
     * membership is inert there.
     */
    public static JsonObject discs(String... itemIds) {
        return Tags.item("minecraft", "music_discs", itemIds);
    }
}
