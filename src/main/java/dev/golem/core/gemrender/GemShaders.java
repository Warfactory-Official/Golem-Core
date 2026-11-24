package dev.golem.core.gemrender;

import com.wf.gemrender.light.LightShaders;
import dev.golem.core.Ids;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Whether GemRender's patch of vanilla's entity cutout stages is the one the static-mesh vertex stage repeats under
 * {@code GOLEM_GEMRENDER_LIGHTS}. Load only after {@code Instancing.isAvailable}.
 */
public final class GemShaders {

    /** NONE: GemRender left a resource pack's copy alone. */
    public enum Patch { NONE, MIRRORED, UNKNOWN }

    //? if >=1.21.11 {
    private static final String NAME = "core/entity";
    private static final String FILE = "entity";
    private static final List<String> VERTEX = List.of(
            "_grl_lm(sample_lightmap(Sampler2, UV2), clamp(float((UV2).y) / 240.0, 0.0, 1.0))",
            "_grl_pos = (Position);", "_grl_normal = Normal;");
    //?} elif >=1.21 {
    /*private static final String NAME = "rendertype_entity_cutout";
    private static final String FILE = NAME;
    private static final List<String> VERTEX = List.of(
            "_grl_lm(texelFetch(Sampler2, UV2 / 16, 0), clamp(float((UV2 / 16).y) / 15.0, 0.0, 1.0))",
            "_grl_pos = (Position);", "_grl_normal = Normal;");
    *///?} else {
    /*private static final String NAME = "rendertype_entity_cutout";
    private static final String FILE = NAME;
    private static final List<String> VERTEX = List.of(
            "_grl_lm(texelFetch(Sampler2, UV2 / 16, 0), clamp(float((UV2 / 16).y) / 15.0, 0.0, 1.0))",
            "_grl_pos = IViewRotMat * (Position);", "_grl_normal = IViewRotMat * Normal;");
    *///?}
    private static final List<String> LIGHTMAP = List.of("_grl_lmColor = c;", "_grl_sky = sky;",
            "return max(c, vec4(1.0 / 255.0));", "_grl_lmColor = vec4(1.0);", "_grl_sky = 0.0;");
    private static final Set<String> INPUTS = Set.of("vec3 _grl_pos", "vec3 _grl_normal", "vec4 _grl_lmColor",
            "float _grl_sky");
    private static final Pattern INPUT = Pattern.compile("(?m)^\\s*in\\s+(\\w+)\\s+(_grl_\\w+)\\s*;");
    private static final Pattern OUTPUT = Pattern.compile("(?m)^\\s*out\\s+\\w+\\s+_grl_\\w+\\s*;");

    private GemShaders() {}

    /** Runs GemRender's own patcher over the vanilla sources in {@code resources}. */
    public static Patch entityCutout(ResourceManager resources) throws IOException {
        String vertex;
        String fragment;
        try {
            // vertex first: GemRender skips a fragment stage whose vertex stage it could not fit
            vertex = LightShaders.vanilla(NAME, true, read(resources, FILE + ".vsh"), 1);
            fragment = LightShaders.vanilla(NAME, false, read(resources, FILE + ".fsh"), 1);
        } catch (LinkageError e) {
            // a GemRender without the four-argument patcher (before 0.1.19)
            return Patch.UNKNOWN;
        }
        Set<String> inputs = new HashSet<>();
        Matcher input = INPUT.matcher(fragment);
        while (input.find()) inputs.add(input.group(1) + " " + input.group(2));
        boolean vertexPatched = OUTPUT.matcher(vertex).find();
        if (inputs.isEmpty() && !vertexPatched) return Patch.NONE;
        boolean mirrored = inputs.equals(INPUTS) && VERTEX.stream().allMatch(vertex::contains)
                && LIGHTMAP.stream().allMatch(vertex::contains);
        return mirrored ? Patch.MIRRORED : Patch.UNKNOWN;
    }

    private static String read(ResourceManager resources, String file) throws IOException {
        try (BufferedReader reader = resources.getResourceOrThrow(Ids.of("minecraft", "shaders/core/" + file))
                .openAsReader()) {
            return reader.lines().collect(Collectors.joining("\n", "", "\n"));
        }
    }
}
