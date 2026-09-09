package dev.golem.core.flywheel.indirect;

//? if neoforge && >=26.1 {

import dev.golem.core.GolemCore;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.Reader;
import org.apache.commons.io.IOUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

// Raw GL compute program for the cull pass. Mojang's RenderPipeline only knows about
// vertex/fragment programs; compute shaders need their own compile + link path.
//
// Loaded once per game session via load() (called lazily on first cull dispatch). The shader
// source has no #moj_import directives, so we don't run Mojang's GlslPreprocessor — just feed
// the raw text to glShaderSource. Uniform locations are resolved at link time and cached.
public final class IndirectComputeProgram {

    private static final Identifier CULL_SHADER = Identifier.fromNamespaceAndPath(GolemCore.MOD_ID, "shaders/core/golem_cull.csh");

    private int programId;
    private int frustumPlanesLoc;
    private int cameraPosROLoc;
    private int modelViewLoc;
    private int projectionInfoLoc; // vec4(znear, P00, P11, _)
    private int pyramidInfoLoc;    // vec4(width, height, levelsMinusOne, _)
    private int hizEnabledLoc;

    public boolean ensureLoaded() {
        if (programId != 0) return true;
        RenderSystem.assertOnRenderThread();
        try {
            String source = readResource(CULL_SHADER);
            int shaderId = compileShader(source);
            programId = linkProgram(shaderId);
            GL20.glDeleteShader(shaderId);
            frustumPlanesLoc = GL20.glGetUniformLocation(programId, "FrustumPlanes");
            cameraPosROLoc = GL20.glGetUniformLocation(programId, "CameraPosRO");
            modelViewLoc = GL20.glGetUniformLocation(programId, "ModelView");
            projectionInfoLoc = GL20.glGetUniformLocation(programId, "ProjectionInfo");
            pyramidInfoLoc = GL20.glGetUniformLocation(programId, "PyramidInfo");
            hizEnabledLoc = GL20.glGetUniformLocation(programId, "HizEnabled");
            return true;
        } catch (IOException | RuntimeException e) {
            GolemCore.LOG.error("Failed to load Golem cull compute shader; INDIRECT culling disabled", e);
            programId = 0;
            return false;
        }
    }

    public void use() {
        GL20.glUseProgram(programId);
    }

    public void setFrustumPlanes(float[] planes24) {
        if (frustumPlanesLoc < 0) return;
        GL20.glUniform4fv(frustumPlanesLoc, planes24);
    }

    public void setCameraPosRO(float x, float y, float z) {
        if (cameraPosROLoc < 0) return;
        GL20.glUniform3f(cameraPosROLoc, x, y, z);
    }

    public void setModelView(Matrix4fc m) {
        if (modelViewLoc < 0) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);
            m.get(buf);
            GL20.glUniformMatrix4fv(modelViewLoc, false, buf);
        }
    }

    public void setProjectionInfo(float znear, float p00, float p11) {
        if (projectionInfoLoc < 0) return;
        GL20.glUniform4f(projectionInfoLoc, znear, p00, p11, 0.0F);
    }

    public void setPyramidInfo(int width, int height, int levelsMinusOne) {
        if (pyramidInfoLoc < 0) return;
        GL20.glUniform4f(pyramidInfoLoc, width, height, levelsMinusOne, 0.0F);
    }

    public void setHizEnabled(boolean enabled) {
        if (hizEnabledLoc < 0) return;
        GL30.glUniform1ui(hizEnabledLoc, enabled ? 1 : 0);
    }

    public void close() {
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
            programId = 0;
        }
    }

    private static String readResource(Identifier id) throws IOException {
        var resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id);
        try (Reader reader = resource.openAsReader()) {
            return IOUtils.toString(reader);
        }
    }

    private static int compileShader(String source) {
        int shader = GL43.glCreateShader(GL43.GL_COMPUTE_SHADER);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new RuntimeException("golem_cull.csh compile failed: " + log);
        }
        return shader;
    }

    private static int linkProgram(int shaderId) {
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, shaderId);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new RuntimeException("golem_cull program link failed: " + log);
        }
        return program;
    }
}
//?}
