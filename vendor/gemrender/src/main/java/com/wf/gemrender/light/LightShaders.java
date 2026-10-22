package com.wf.gemrender.light;

import com.wf.gemrender.GemRender;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL31C.GL_INVALID_INDEX;
import static org.lwjgl.opengl.GL31C.glGetUniformBlockIndex;
import static org.lwjgl.opengl.GL31C.glUniformBlockBinding;

/**
 * Source patches that make a surface shader add {@code albedo * gemrender_lights(p, n)} before fog.
 * Albedo = lit colour / lightmap sample; lightmap floor 7/255 (LightTexture's final lerp to 0.75 by 0.04).
 * Anchor missing => throw: a changed upstream shader must not silently go unlit.
 */
public final class LightShaders {
    /** Vanilla core programs; each pairs one vsh with the same-named fsh. */
    private static final Set<String> VANILLA = Set.of("rendertype_solid", "rendertype_cutout_mipped",
            "rendertype_cutout", "rendertype_translucent", "rendertype_tripwire", "rendertype_entity_solid",
            "rendertype_entity_cutout", "rendertype_entity_cutout_no_cull", "rendertype_entity_cutout_no_cull_z_offset",
            "rendertype_entity_translucent", "rendertype_entity_translucent_cull", "rendertype_entity_smooth_cutout",
            "rendertype_entity_no_outline", "rendertype_entity_decal", "rendertype_item_entity_translucent_cull",
            "rendertype_armor_cutout_no_cull", "particle");

    private static final Pattern VANILLA_LIGHTMAP = Pattern.compile(
            "minecraft_sample_lightmap\\(Sampler2, UV2\\)|texelFetch\\(Sampler2, UV2 / 16, 0\\)");
    private static final Pattern VANILLA_FOG = Pattern.compile("fragColor = linear_fog\\(color,");
    private static final Pattern MAIN = Pattern.compile("void main\\(\\) \\{");

    private static final Pattern SODIUM_LIGHTMAP = Pattern.compile(
            "texture\\(u_LightTex, _vert_tex_light_coord\\)");
    private static final Pattern SODIUM_POSITION = Pattern.compile(
            "vec3 position = _vert_position \\+ translation;");
    private static final Pattern SODIUM_FOG = Pattern.compile("fragColor = _linearFog\\((\\w+),");
    private static final ResourceLocation SODIUM_VERTEX = ResourceLocation.fromNamespaceAndPath("sodium",
            "blocks/block_layer_opaque.vsh");
    private static final ResourceLocation SODIUM_FRAGMENT = ResourceLocation.fromNamespaceAndPath("sodium",
            "blocks/block_layer_opaque.fsh");

    private static final ResourceLocation FLYWHEEL_COMMON = ResourceLocation.fromNamespaceAndPath("flywheel",
            "internal/common.frag");
    private static final Pattern FLYWHEEL_MAIN = Pattern.compile("void _flw_main\\(\\) \\{");
    private static final Pattern FLYWHEEL_LIGHT = Pattern.compile("    vec4 lightColor = vec4\\(1\\.\\);");
    private static final Pattern FLYWHEEL_FOG = Pattern.compile("    color = flw_fogFilter\\(color\\);");

    private static final String VERTEX_DECLARATIONS = """
            out vec3 _grl_pos;
            out vec3 _grl_normal;
            out vec4 _grl_lmColor;
            vec4 _grl_lm(vec4 c) {
                _grl_lmColor = c;
                return c;
            }
            """;

    private static final String FRAGMENT_INPUTS = """
            in vec3 _grl_pos;
            in vec3 _grl_normal;
            in vec4 _grl_lmColor;
            """;

    private static final String ADD = ".rgb += %1$s.rgb / max(_grl_lmColor.rgb, vec3(1.0 / 255.0)) * %2$s;\n    ";

    private static String include;

    private LightShaders() {
    }

    /** GLSL text spliced after {@code #version}; defines from the Java constants. */
    public static synchronized String include() {
        if (include == null) {
            try (InputStream in = LightShaders.class.getResourceAsStream(
                    "/assets/" + GemRender.MOD_ID + "/shaders/include/lights.glsl")) {
                include = "#define GEMRENDER_LIGHT_MAX " + Lights.MAX + "\n"
                        + "#define GEMRENDER_LIGHT_GRID " + LightGrid.GRID + "\n"
                        + "#define GEMRENDER_LIGHT_COOKIE_SIZE " + LightCookies.SIZE + "\n"
                        + "#define GEMRENDER_LIGHT_SHADOW_RES " + LightShadows.RES + "\n"
                        + "#define GEMRENDER_LIGHT_SHADOW_TILES " + LightShadows.TILES + "\n"
                        + "#define GEMRENDER_LIGHT_SHADOW_MAX " + LightShadows.MAX + "\n"
                        + "#define GEMRENDER_LIGHT_OCCUPANCY " + LightOccupancy.SIZE + "\n"
                        + new String(in.readAllBytes(), StandardCharsets.UTF_8) + "\n";
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return include;
    }

    public static String vanilla(String name, boolean vertex, String source) {
        if (!VANILLA.contains(name)) {
            return source;
        }
        if (vertex) {
            String position = source.contains("uniform vec3 ChunkOffset;") ? "Position + ChunkOffset" : "Position";
            String normal = source.contains("in vec3 Normal;") ? "Normal" : "vec3(0.0)";
            String patched = replaceAll(name, source, VANILLA_LIGHTMAP, "_grl_lm($0)");
            patched = replaceOne(name, patched, MAIN, "void _grl_main() {");
            return afterVersion(name, patched, VERTEX_DECLARATIONS) + "\nvoid main() {\n    _grl_lmColor = vec4(1.0);\n"
                    + "    _grl_main();\n    _grl_pos = " + position + ";\n    _grl_normal = " + normal + ";\n}\n";
        }
        String patched = replaceOne(name, source, MAIN, "$0\n    gemrender_lightPrepare(_grl_pos);");
        patched = replaceOne(name, patched, VANILLA_FOG,
                "color" + ADD.formatted("color", "gemrender_lights(_grl_pos, gemrender_lightNormal(_grl_normal))") + "$0");
        return afterVersion(name, patched, include() + FRAGMENT_INPUTS);
    }

    public static String sodium(ResourceLocation id, String source) {
        String name = id.toString();
        if (id.equals(SODIUM_VERTEX)) {
            String patched = replaceAll(name, source, SODIUM_LIGHTMAP, "_grl_lm($0)");
            patched = replaceOne(name, patched, SODIUM_POSITION, "$0\n    _grl_pos = position;");
            return afterVersion(name, patched, VERTEX_DECLARATIONS);
        }
        if (id.equals(SODIUM_FRAGMENT)) {
            String patched = replaceOne(name, source, MAIN, "$0\n    gemrender_lightPrepare(_grl_pos);");
            patched = replaceOne(name, patched, SODIUM_FOG,
                    "$1" + ADD.formatted("$1", "gemrender_lights(_grl_pos, gemrender_lightFaceNormal(_grl_pos))") + "$0");
            return afterVersion(name, patched, include() + FRAGMENT_INPUTS);
        }
        return source;
    }

    /** Flywheel concatenates includes; no #version in the file. Light only where the lightmap applies. */
    public static String flywheel(ResourceLocation id, String source) {
        if (!id.equals(FLYWHEEL_COMMON)) {
            return source;
        }
        String name = id.toString();
        String patched = replaceOne(name, source, FLYWHEEL_MAIN, "$0\n    vec3 _grl_pos = flw_vertexPos.xyz - flw_cameraPos;"
                + "\n    gemrender_lightPrepare(_grl_pos);");
        patched = replaceOne(name, patched, FLYWHEEL_LIGHT, "    vec3 _grl_albedo = color.rgb;\n$0");
        patched = replaceOne(name, patched, FLYWHEEL_FOG, "    if (flw_material.useLight) {\n"
                + "        color.rgb += _grl_albedo * gemrender_lights(_grl_pos, gemrender_lightNormal(flw_vertexNormal));\n"
                + "    }\n$0");
        return include() + patched;
    }

    /** After link: block -> {@link LightFrame#UBO_BINDING}, samplers -> units. No block => untouched. */
    public static void bindProgram(int program) {
        int block = glGetUniformBlockIndex(program, "GemRenderLights");
        if (block == GL_INVALID_INDEX) {
            return;
        }
        LightFrame.getInstance().ensureBuffers();
        glUniformBlockBinding(program, block, LightFrame.UBO_BINDING);
        int previous = glGetInteger(GL_CURRENT_PROGRAM);
        glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "_grl_cookies"), LightCookies.UNIT);
        glUniform1i(glGetUniformLocation(program, "_grl_grid"), LightGrid.UNIT);
        glUniform1i(glGetUniformLocation(program, "_grl_shadows"), LightShadows.UNIT);
        glUseProgram(previous);
    }

    static boolean standDown() {
        //? if iris {
        return com.wf.gemrender.iris.ShaderPacks.inUse();
        //?} else {
        /*return false;
        *///?}
    }

    private static String afterVersion(String name, String source, String text) {
        Matcher version = Pattern.compile("(?m)^#version.*$").matcher(source);
        if (!version.find()) {
            throw new IllegalStateException("GemRender lights: no #version in " + name);
        }
        return source.substring(0, version.end()) + "\n" + text + source.substring(version.end());
    }

    private static String replaceOne(String name, String source, Pattern anchor, String replacement) {
        Matcher matcher = anchor.matcher(source);
        int found = 0;
        while (matcher.find()) {
            found++;
        }
        if (found != 1) {
            throw new IllegalStateException("GemRender lights: " + name + " has " + found + " of /" + anchor + "/");
        }
        return anchor.matcher(source).replaceFirst(replacement);
    }

    private static String replaceAll(String name, String source, Pattern anchor, String replacement) {
        if (!anchor.matcher(source).find()) {
            throw new IllegalStateException("GemRender lights: " + name + " has no /" + anchor + "/");
        }
        return anchor.matcher(source).replaceAll(replacement);
    }
}
