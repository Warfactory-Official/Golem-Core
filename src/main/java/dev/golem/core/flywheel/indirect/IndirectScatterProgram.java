package dev.golem.core.flywheel.indirect;

//? if neoforge && >=26.1 {

import dev.golem.core.GolemCore;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

import java.io.IOException;
import java.io.Reader;

// Compute program for the GPU memcpy that drains the staging-buffer ring into destination
// VBOs. One workgroup per scatter record; bindings 0=Copies, 1=Src (staging ring), 2=Dst.
public final class IndirectScatterProgram {

    private static final Identifier SCATTER_SHADER = Identifier.fromNamespaceAndPath(GolemCore.MOD_ID, "shaders/core/golem_scatter.csh");

    private int programId;

    public boolean ensureLoaded() {
        if (programId != 0) return true;
        RenderSystem.assertOnRenderThread();
        try {
            String source = readResource(SCATTER_SHADER);
            int shaderId = compileShader(source);
            programId = linkProgram(shaderId);
            GL20.glDeleteShader(shaderId);
            return true;
        } catch (IOException | RuntimeException e) {
            GolemCore.LOG.error("Failed to load Golem scatter compute shader; staging uploads disabled", e);
            programId = 0;
            return false;
        }
    }

    public void use() {
        GL20.glUseProgram(programId);
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
            throw new RuntimeException("golem_scatter.csh compile failed: " + log);
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
            throw new RuntimeException("golem_scatter program link failed: " + log);
        }
        return program;
    }
}
//?}
