package dev.golem.core.render;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.golem.core.GolemCore;
import dev.golem.core.Ids;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.sources.LazyLoadedImage;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * An atlas sprite source that recolours one greyscale texture into many, at resource-load time.
 *
 * <p>Each named permutation gives a light and a dark colour; the source texture's band (white down to
 * {@code #505050} by default) is mapped onto them per channel by a {@link ColorRamp}, and the result is
 * stitched under the source id plus {@code _<permutation>}. One 16×16 greyscale plate plus a hundred
 * declared colours is a hundred sprites the mod never ships as files, recomputed on every resource
 * reload — the same thing 1.7's {@code TextureAtlasSpriteMutatable} did before the atlas stopped being
 * built from mutable icons.
 *
 * <p>Declare it in code through {@link Sprites}; the JSON form exists so a resource pack can write the
 * same thing by hand:
 *
 * <pre>{@code
 * {
 *   "type": "golemcore:interpolated_remap",
 *   "textures": ["hbm:items/plate_cast"],
 *   "band": { "light": "#FFFFFF", "dark": "#505050" },
 *   "permutations": { "iron": { "light": "#FFFFFF", "dark": "#353535" } }
 * }
 * }</pre>
 *
 * <p>Sources run in the order the atlas lists them and a later one wins, which is why Core puts its
 * code-declared sources <em>first</em>: a pack dropping a real PNG at the generated sprite's path is
 * picked up by the directory listing afterwards and replaces it, so overriding one recoloured sprite
 * stays as simple as shipping the file it would have generated.
 *
 * <p>Metadata on the source texture is not carried over — a remapped sprite is a still frame, as it is
 * for vanilla's own palette source.
 */
public final class InterpolatedRemapSource implements SpriteSource {

    /** The id a resource pack names this source by. */
    public static final String TYPE_NAMESPACE = GolemCore.MOD_ID;
    public static final String TYPE_PATH = "interpolated_remap";

    /** A light/dark colour pair: either end of a band. */
    public record Band(int light, int dark) {

        public Band {
            light &= 0xFFFFFF;
            dark &= 0xFFFFFF;
        }
    }

    /** The band HBM's shape art is drawn in, and the default when a declaration does not say. */
    public static final Band DEFAULT_BAND = new Band(ColorRamp.DEFAULT_SOURCE_LIGHT, ColorRamp.DEFAULT_SOURCE_DARK);

    private static final Codec<Integer> COLOUR = Codec.either(Codec.INT, Codec.STRING).comapFlatMap(
            either -> either.map(DataResult::success, InterpolatedRemapSource::parseColour),
            colour -> Either.right(String.format(Locale.ROOT, "#%06X", colour & 0xFFFFFF)));

    private static final Codec<Band> BAND_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            COLOUR.fieldOf("light").forGetter(Band::light),
            COLOUR.fieldOf("dark").forGetter(Band::dark)).apply(instance, Band::new));

    public static final MapCodec<InterpolatedRemapSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.listOf().fieldOf("textures").forGetter(source -> source.textures),
            Codec.STRING.optionalFieldOf("separator", "_").forGetter(source -> source.separator),
            BAND_CODEC.optionalFieldOf("band", DEFAULT_BAND).forGetter(source -> source.band),
            Codec.unboundedMap(Codec.STRING, BAND_CODEC).fieldOf("permutations").forGetter(source -> source.permutations))
            .apply(instance, InterpolatedRemapSource::new));

    private final List<String> textures;
    private final String separator;
    private final Band band;
    private final Map<String, Band> permutations;

    InterpolatedRemapSource(List<String> textures, String separator, Band band, Map<String, Band> permutations) {
        this.textures = List.copyOf(textures);
        this.separator = separator;
        this.band = band;
        this.permutations = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(permutations));
    }

    private static DataResult<Integer> parseColour(String value) {
        String digits = value.startsWith("#") ? value.substring(1)
                : value.startsWith("0x") || value.startsWith("0X") ? value.substring(2)
                : value;
        try {
            return DataResult.success(Integer.parseUnsignedInt(digits, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Not a colour: " + value);
        }
    }

    @Override
    public void run(ResourceManager resources, Output output) {
        Map<String, ColorRamp> ramps = new LinkedHashMap<>();
        permutations.forEach((name, target) -> {
            try {
                ramps.put(name, new ColorRamp(band.light(), band.dark(), target.light(), target.dark()));
            } catch (IllegalArgumentException e) {
                GolemCore.LOG.warn("Skipping remap permutation {}: {}", name, e.getMessage());
            }
        });
        if (ramps.isEmpty()) {
            return;
        }

        for (String texture : textures) {
            var id = Ids.parse(texture);
            var file = SpriteSource.TEXTURE_ID_CONVERTER.idToFile(id);
            var resource = resources.getResource(file);
            if (resource.isEmpty()) {
                GolemCore.LOG.warn("Missing source texture {} for interpolated remap", file);
                continue;
            }
            LazyLoadedImage image = new LazyLoadedImage(file, resource.get(), ramps.size());
            ramps.forEach((name, ramp) -> {
                var sprite = id.withSuffix(separator + name);
                output.add(sprite, new Remapped(image, ramp, sprite.toString()));
            });
        }
    }

    //? if >=26.1 {
    @Override
    public MapCodec<? extends SpriteSource> codec() {
        return CODEC;
    }
    //?} else {
    /*@Override
    public net.minecraft.client.renderer.texture.atlas.SpriteSourceType type() {
        return Sprites.remapSourceType();
    }
    *///?}

    /**
     * One recoloured sprite, produced when the stitcher asks for it. The shared source image is
     * reference counted: every permutation releases it once, and the last release frees it.
     */
    private record Remapped(LazyLoadedImage image, ColorRamp ramp, String id)
            //? if >=26.1 {
            implements SpriteSource.DiscardableLoader {
            //?} else {
            /*implements SpriteSource.SpriteSupplier {
            *///?}

        //? if >=26.1 {
        @Override
        public SpriteContents get(SpriteResourceLoader loader) {
            return contents();
        }
        //?} elif >=1.21 {
        /*@Override
        public SpriteContents apply(SpriteResourceLoader loader) {
            return contents();
        }
        *///?} else {
        /*@Override
        public SpriteContents get() {
            return contents();
        }
        *///?}

        private SpriteContents contents() {
            try {
                // What mappedCopy hands the operator is not the same on both sides of 26.1. 26.1
                // converts each pixel with ARGB.fromABGR before the call and ARGB.toABGR after it;
                // every version below hands over the raw int, which is ABGR in memory. Feeding one
                // version's operator to the other swaps red and blue — gold comes out blue.
                //? if >=26.1 {
                var mapped = image.get().mappedCopy(ramp::applyArgb);
                //?} else {
                /*var mapped = image.get().mappedCopy(ramp.abgrOperator());
                *///?}
                //? if >=26.1 {
                return new SpriteContents(Ids.parse(id), new FrameSize(mapped.getWidth(), mapped.getHeight()), mapped);
                //?} elif >=1.21 {
                /*return new SpriteContents(Ids.parse(id), new FrameSize(mapped.getWidth(), mapped.getHeight()), mapped,
                        net.minecraft.server.packs.resources.ResourceMetadata.EMPTY);
                *///?} else {
                /*return new SpriteContents(Ids.parse(id), new FrameSize(mapped.getWidth(), mapped.getHeight()), mapped,
                        net.minecraft.client.resources.metadata.animation.AnimationMetadataSection.EMPTY);
                *///?}
            } catch (Exception e) {
                GolemCore.LOG.error("Unable to remap {}", id, e);
                return MissingTextureAtlasSprite.create();
            } finally {
                image.release();
            }
        }

        @Override
        public void discard() {
            image.release();
        }
    }
}
