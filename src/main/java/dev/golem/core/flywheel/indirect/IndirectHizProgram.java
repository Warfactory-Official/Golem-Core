package dev.golem.core.flywheel.indirect;

//? if >=26.1 {

import dev.golem.core.GolemCore;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.Reader;
import org.apache.commons.io.IOUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

// Owns the two HiZ build compute programs (vanilla depth → mip 0, and mip k → mip k+1) used
// by HizPyramid.rebuild. Same compile/link/cache pattern as IndirectComputeProgram — Mojang's
// pipeline doesn't manage compute, so we go raw GL.
public final class IndirectHizProgram {

    private static final Identifier FIRST_SHADER = Identifier.fromNamespaceAndPath(GolemCore.MOD_ID, "shaders/core/golem_hiz_first.csh");
    private static final Identifier DOWN_SHADER = Identifier.fromNamespaceAndPath(GolemCore.MOD_ID, "shaders/core/golem_hiz_down.csh");

    private int firstProgramId;
    private int firstMip0SizeLoc;

    private int downProgramId;
    private int downDstSizeLoc;

    public boolean ensureLoaded() {
        if (firstProgramId != 0 && downProgramId != 0) return true;
        RenderSystem.assertOnRenderThread();
        try {
            if (firstProgramId == 0) {
                firstProgramId = compileLink(FIRST_SHADER, "golem_hiz_first");
                firstMip0SizeLoc = GL20.glGetUniformLocation(firstProgramId, "Mip0Size");
            }
            if (downProgramId == 0) {
                downProgramId = compileLink(DOWN_SHADER, "golem_hiz_down");
                downDstSizeLoc = GL20.glGetUniformLocation(downProgramId, "DstSize");
            }
            return true;
        } catch (IOException | RuntimeException e) {
            GolemCore.LOG.error("Failed to load Golem HiZ compute shaders; INDIRECT occlusion cull disabled", e);
            close();
            return false;
        }
    }

    public void useFirst() {
        GL20.glUseProgram(firstProgramId);
    }

    public void useDown() {
        GL20.glUseProgram(downProgramId);
    }

    public void setMip0Size(int w, int h) {
        if (firstMip0SizeLoc < 0) return;
        GL20.glUniform2i(firstMip0SizeLoc, w, h);
    }

    public void setDownDstSize(int w, int h) {
        if (downDstSizeLoc < 0) return;
        GL20.glUniform2i(downDstSizeLoc, w, h);
    }

    public void close() {
        if (firstProgramId != 0) {
            GL20.glDeleteProgram(firstProgramId);
            firstProgramId = 0;
        }
        if (downProgramId != 0) {
            GL20.glDeleteProgram(downProgramId);
            downProgramId = 0;
        }
    }

    private static int compileLink(Identifier id, String label) throws IOException {
        String source = readResource(id);
        int shader = GL43.glCreateShader(GL43.GL_COMPUTE_SHADER);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new RuntimeException(label + " compile failed: " + log);
        }
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, shader);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(shader);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new RuntimeException(label + " link failed: " + log);
        }
        return program;
    }

    private static String readResource(Identifier id) throws IOException {
        var resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id);
        try (Reader reader = resource.openAsReader()) {
            return IOUtils.toString(reader);
        }
    }
}
//?}
