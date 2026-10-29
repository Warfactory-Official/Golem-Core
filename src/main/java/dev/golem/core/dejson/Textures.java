package dev.golem.core.dejson;

import dev.golem.core.render.ColorRamp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** Build-time sprite remaps, emitted as ordinary PNG resources alongside the declared models. */
public final class Textures {
    private record Remap(String source, ColorRamp ramp) {}
    private static final Map<String, Remap> REMAPS = new LinkedHashMap<>();

    private Textures() {
    }
    /** The same per-channel interpolation used by load-time atlas remaps. */
    public static void remap(String target, String source, int light, int dark) {
        declare(target, source, ColorRamp.of(light, dark));
    }

    /** Publishes the unchanged source pixels under another sprite id. */
    public static void copy(String target, String source) {
        declare(target, source, new ColorRamp(0xFFFFFF, 0, 0xFFFFFF, 0));
    }

    private static void declare(String target, String source, ColorRamp ramp) {
        Models.requireId(target, "target sprite");
        Models.requireId(source, "source sprite");
        if (REMAPS.putIfAbsent(target, new Remap(source, ramp)) != null) {
            throw new DejsonException("sprite " + target + " is declared twice");
        }
        Dejson.generatedSprite(target, source);
    }

    private static String path(String sprite) {
        var colon = sprite.indexOf(':');
        return "assets/" + sprite.substring(0, colon) + "/textures/" + sprite.substring(colon + 1) + ".png";
    }

    private static BufferedImage image(Remap remap, Path authored) throws IOException {
        var relative = path(remap.source());
        BufferedImage source;
        if (authored != null && Files.exists(authored.resolve(relative))) {
            source = ImageIO.read(authored.resolve(relative).toFile());
        } else {
            try (var stream = Textures.class.getClassLoader().getResourceAsStream(relative)) {
                if (stream == null) throw new IOException("Missing source texture " + relative);
                source = ImageIO.read(stream);
            }
        }
        if (source == null) throw new IOException("Not a readable PNG: " + relative);
        if (remap.ramp().sourceLight() == remap.ramp().targetLight()
                && remap.ramp().sourceDark() == remap.ramp().targetDark()) return source;
        var image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (var y = 0; y < source.getHeight(); y++) {
            for (var x = 0; x < source.getWidth(); x++) {
                image.setRGB(x, y, remap.ramp().applyArgb(source.getRGB(x, y)));
            }
        }
        return image;
    }

    static boolean dependencySource(String target) {
        var remap = REMAPS.get(target);
        return Textures.class.getClassLoader().getResource(path(remap == null ? target : remap.source())) != null;
    }

    static DejsonWriter.Report write(Path out, Path authored) throws IOException {
        var written = 0;
        var deferred = 0;
        for (var entry : REMAPS.entrySet()) {
            var relative = path(entry.getKey());
            if (authored != null && Files.exists(authored.resolve(relative))) {
                deferred++;
                continue;
            }
            var target = out.resolve(relative);
            Files.createDirectories(target.getParent());
            if (!ImageIO.write(image(entry.getValue(), authored), "PNG", target.toFile())) {
                throw new IOException("PNG writer unavailable for " + target);
            }
            written++;
        }
        return new DejsonWriter.Report(written, deferred);
    }

    static List<DejsonWriter.Mismatch> verify(Path authored) {
        var mismatches = new ArrayList<DejsonWriter.Mismatch>();
        for (var entry : REMAPS.entrySet()) {
            var relative = path(entry.getKey());
            var file = authored.resolve(relative);
            if (!Files.exists(file)) continue;
            try {
                var expected = image(entry.getValue(), authored);
                var actual = ImageIO.read(file.toFile());
                if (actual == null || actual.getWidth() != expected.getWidth() || actual.getHeight() != expected.getHeight()) {
                    mismatches.add(new DejsonWriter.Mismatch(relative, "PNG dimensions differ"));
                    continue;
                }
                var differs = false;
                for (var y = 0; y < expected.getHeight() && !differs; y++) {
                    for (var x = 0; x < expected.getWidth(); x++) {
                        if (actual.getRGB(x, y) != expected.getRGB(x, y)) { differs = true; break; }
                    }
                }
                if (differs) mismatches.add(new DejsonWriter.Mismatch(relative, "PNG pixels differ"));
            } catch (IOException failure) {
                throw new UncheckedIOException("Cannot verify " + file, failure);
            }
        }
        return mismatches;
    }

    static void reset() { REMAPS.clear(); }
}
