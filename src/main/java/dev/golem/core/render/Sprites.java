package dev.golem.core.render;

import dev.golem.core.GolemCore;
import dev.golem.core.Ids;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;

/**
 * Sprites a mod computes instead of shipping, declared in code and built while the atlas is stitched.
 *
 * <p>The unit is a {@linkplain InterpolatedRemapSource remap}: one greyscale texture plus a table of
 * named colour pairs, producing one sprite per pair. Declaring
 *
 * <pre>{@code
 * Sprites.remap(Sprites.Atlas.ITEMS)
 *         .texture("hbm:items/plate_cast")
 *         .permutation("iron", 0xFFFFFF, 0x353535)
 *         .permutation("copper", 0xFDCA88, 0x601E0D)
 *         .declare();
 * }</pre>
 *
 * puts {@code hbm:items/plate_cast_iron} and {@code hbm:items/plate_cast_copper} in the atlas, from one
 * 16×16 file. A model references them by those ids like any other sprite.
 *
 * <p>Declaration is the default path and needs no resource of any kind — no atlas JSON, no generated
 * asset, nothing on disk but the greyscale source. The same source type is registered for resource
 * packs under {@code golemcore:interpolated_remap}, so a pack can write one by hand into its own
 * {@code atlases/} file; a consumer that ships only JSON and never declares anything in code calls
 * {@link #install()} once so the type is known before the pack is read.
 *
 * <p>Declared sources run <em>before</em> the atlas's own, so a real PNG at the generated sprite's path
 * still wins: overriding one recoloured sprite in a pack means shipping that one file, and nothing here
 * takes that away.
 *
 * <p>Client only, and called from client initialisation: nothing here touches a dedicated server.
 */
public final class Sprites {

    private Sprites() {}

    /**
     * Which atlas a declaration lands in.
     *
     * <p>{@link #ITEMS} is its own atlas from 1.21.4 and part of the block atlas before it, which is
     * exactly the sort of difference a consumer should not be spelling out.
     */
    public enum Atlas {
        BLOCKS,
        ITEMS;

        /** The atlas definition id — what {@code atlases/<path>.json} is read for. */
        public String id() {
            //? if >=26.1 {
            return this == ITEMS ? "minecraft:items" : "minecraft:blocks";
            //?} else {
            /*return "minecraft:blocks";
            *///?}
        }
    }

    private static final Map<String, List<SpriteSource>> DECLARED = new ConcurrentHashMap<>();
    private static volatile boolean installed;

    /** Starts a remap declaration for the given atlas. */
    public static Remap remap(Atlas atlas) {
        return new Remap(atlas);
    }

    /**
     * Registers Core's sprite source types with the client, so an atlas JSON naming one parses. Called
     * for you by {@link Remap#declare()}; call it directly when the only user is a resource pack.
     *
     * <p>Idempotent, and cheap enough to call from every client initialiser.
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        registerRemapType();
    }

    /** The id a remap source is named by, in JSON and in the type registry. */
    private static String typeId() {
        return InterpolatedRemapSource.TYPE_NAMESPACE + ":" + InterpolatedRemapSource.TYPE_PATH;
    }

    //? if neoforge && >=26.1 {
    private static void registerRemapType() {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterSpriteSourcesEvent event) ->
                        event.register(Ids.parse(typeId()), InterpolatedRemapSource.CODEC));
    }
    //?}

    //? if neoforge && <26.1 {
    /*private static void registerRemapType() {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent event) ->
                        remapType = event.register(Ids.parse(typeId()), InterpolatedRemapSource.CODEC));
    }
    *///?}

    //? if fabric && >=26.1 {
    /*private static void registerRemapType() {
        dev.golem.core.mixin.SpriteSourceTypesAccess.golem$idMapper()
                .put(Ids.parse(typeId()), InterpolatedRemapSource.CODEC);
    }
    *///?}

    //? if fabric && >=1.21 && <26.1 {
    /*private static void registerRemapType() {
        remapType = new net.minecraft.client.renderer.texture.atlas.SpriteSourceType(InterpolatedRemapSource.CODEC);
        dev.golem.core.mixin.SpriteSourceTypesAccess.golem$types().put(Ids.parse(typeId()), remapType);
    }
    *///?}

    //? if <1.21 {
    /*private static void registerRemapType() {
        // 1.20.1's SpriteSourceType holds a plain Codec; every version after it holds the MapCodec.
        remapType = new net.minecraft.client.renderer.texture.atlas.SpriteSourceType(
                InterpolatedRemapSource.CODEC.codec());
        dev.golem.core.mixin.SpriteSourceTypesAccess.golem$types().put(Ids.parse(typeId()), remapType);
    }
    *///?}

    //? if <26.1 {
    /*private static volatile net.minecraft.client.renderer.texture.atlas.SpriteSourceType remapType;

    /^* The registered type, which a source instance has to name on the versions that ask it to. ^/
    static net.minecraft.client.renderer.texture.atlas.SpriteSourceType remapSourceType() {
        if (remapType == null) {
            install();
        }
        return remapType;
    }
    *///?}

    /**
     * The sources declared for an atlas, in declaration order. Called by Core's own atlas hook; a
     * consumer has no reason to.
     */
    public static List<SpriteSource> declaredFor(String atlasId) {
        List<SpriteSource> sources = DECLARED.get(atlasId);
        return sources == null ? List.of() : sources;
    }

    /** A remap under construction. */
    public static final class Remap {

        private final Atlas atlas;
        private final List<String> textures = new ArrayList<>();
        private final Map<String, InterpolatedRemapSource.Band> permutations = new LinkedHashMap<>();
        private InterpolatedRemapSource.Band band = InterpolatedRemapSource.DEFAULT_BAND;
        private String separator = "_";

        private Remap(Atlas atlas) {
            this.atlas = atlas;
        }

        /** A greyscale source texture, by sprite id ({@code "hbm:items/plate_cast"}). */
        public Remap texture(String texture) {
            textures.add(texture);
            return this;
        }

        /** Several source textures, each recoloured into every declared permutation. */
        public Remap textures(Collection<String> textures) {
            this.textures.addAll(textures);
            return this;
        }

        /**
         * The band the source art is drawn in. Defaults to white down to {@code 0x505050}, the band
         * HBM's shape art uses; give another when the art is drawn to a different one.
         */
        public Remap band(int light, int dark) {
            this.band = new InterpolatedRemapSource.Band(light, dark);
            return this;
        }

        /** What goes between the source id and the permutation name. Defaults to {@code "_"}. */
        public Remap separator(String separator) {
            this.separator = separator;
            return this;
        }

        /** One output sprite: {@code <texture><separator><name>}, drawn in {@code light}..{@code dark}. */
        public Remap permutation(String name, int light, int dark) {
            permutations.put(name, new InterpolatedRemapSource.Band(light, dark));
            return this;
        }

        /** Whether anything would be generated; a shape no material declares produces nothing. */
        public boolean isEmpty() {
            return textures.isEmpty() || permutations.isEmpty();
        }

        /**
         * Validates the declaration and hands it to the atlas.
         *
         * @throws IllegalArgumentException if the band cannot be interpolated, or nothing was declared
         */
        public void declare() {
            if (textures.isEmpty()) {
                throw new IllegalArgumentException("A remap needs at least one source texture");
            }
            if (permutations.isEmpty()) {
                throw new IllegalArgumentException("A remap needs at least one permutation");
            }
            // Fail here rather than mid-stitch: the band is the one part a caller can get wrong.
            permutations.forEach((name, target) ->
                    new ColorRamp(band.light(), band.dark(), target.light(), target.dark()));

            install();
            DECLARED.computeIfAbsent(atlas.id(), key -> new CopyOnWriteArrayList<>())
                    .add(new InterpolatedRemapSource(textures, separator, band, permutations));
            GolemCore.LOG.debug("Declared {} remapped sprite(s) for {}",
                    textures.size() * permutations.size(), atlas.id());
        }
    }
}
