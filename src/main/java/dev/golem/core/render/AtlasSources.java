package dev.golem.core.render;

import java.util.List;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;

/**
 * Write access to the source list vanilla parsed for one atlas, so Core can add the ones a mod declared
 * in code rather than in an {@code atlases/} file.
 *
 * <p>There is no hook for this on any loader: the list is built from the resource stack, read once, and
 * handed straight to the stitcher. Nothing can be access-widened into existence either, since the list
 * is assembled inside the method that returns it.
 *
 * @see Sprites
 */
public interface AtlasSources {

    /**
     * Puts {@code sources} in front of the ones the atlas declared.
     *
     * <p>In front, not behind: a later source overwrites an earlier one's sprite of the same id, so
     * running first is what lets a resource pack override a generated sprite by shipping the PNG it
     * would have generated. The atlas's own directory listing picks that file up afterwards.
     */
    void golem$prependSources(List<SpriteSource> sources);
}
