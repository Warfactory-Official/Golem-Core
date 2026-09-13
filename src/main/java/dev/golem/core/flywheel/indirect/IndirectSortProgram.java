package dev.golem.core.flywheel.indirect;

//? if >=26.1 {

import dev.golem.core.GolemCore;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

import java.io.IOException;
import java.io.Reader;

// Holder for the GPU sort programs over translucent visList slices. Two paths share this
// loader:
//   - Multi-pass radix (hist → scan → scatter), 2 passes covering the high 16 bits of each
//     entry's .x field (the depth key). The .y field (32-bit instance index) is carried
//     along by the scatter as an 8-byte payload but never inspected.
//   - Single-workgroup bitonic, used when every draw in a sortable group has live ≤ TILE_SIZE.
// Same loader pattern as IndirectComputeProgram (compile, link, cache uniform locations) —
// Mojang's RenderPipeline only handles vertex/fragment programs.
public final class IndirectSortProgram {

    // TILE_SIZE matches local_size_x in every sort shader. 256 chosen so the scatter's
    // per-warp-per-bucket prefix-sum table fits in shared mem on every desktop driver: at
    // min subgroupSize = 16 (Intel Arc), MAX_WARPS = TILE_SIZE / 16 = 16 ≤ 32.
    public static final int TILE_SIZE = 256;
    public static final int BUCKETS = 256;
    // Two byte-radix passes over bits 16..31 of each entry's .x field — the high 16 bits hold
    // the depth key (low 16 of .x stay 0 from cull). The .y payload is carried by scatter and
    // never inspected; same-key entries retain their cull-atomic-write order.
    public static final int PASSES = 2;
    // First byte sorted is bits 16..23 of .x. Pass N covers bits (KEY_SHIFT_BASE + 8*N)..+7.
    public static final int KEY_SHIFT_BASE = 16;

    private static final Identifier HIST_SHADER    = id("golem_sort_hist.csh");
    private static final Identifier SCAN_SHADER    = id("golem_sort_scan.csh");
    private static final Identifier SCATTER_SHADER = id("golem_sort_scatter.csh");
    private static final Identifier BITONIC_SHADER = id("golem_sort_bitonic.csh");

    private int histProgram;
    private int scanProgram;
    private int scatterProgram;
    private int bitonicProgram;

    private int histShiftLoc;
    private int histNumTilesLoc;
    private int scanNumTilesLoc;
    private int scatterShiftLoc;
    private int scatterNumTilesLoc;

    public boolean ensureLoaded() {
        if (histProgram != 0) return true;
        RenderSystem.assertOnRenderThread();
        try {
            histProgram    = compileProgram(HIST_SHADER);
            scanProgram    = compileProgram(SCAN_SHADER);
            scatterProgram = compileProgram(SCATTER_SHADER);
            bitonicProgram = compileProgram(BITONIC_SHADER);
            histShiftLoc       = GL20.glGetUniformLocation(histProgram, "Shift");
            histNumTilesLoc    = GL20.glGetUniformLocation(histProgram, "NumTiles");
            scanNumTilesLoc    = GL20.glGetUniformLocation(scanProgram, "NumTiles");
            scatterShiftLoc    = GL20.glGetUniformLocation(scatterProgram, "Shift");
            scatterNumTilesLoc = GL20.glGetUniformLocation(scatterProgram, "NumTiles");
            return true;
        } catch (IOException | RuntimeException e) {
            GolemCore.LOG.error("Failed to load Golem sort compute shaders; translucent depth-sort disabled", e);
            close();
            return false;
        }
    }

    public void useHist(int shift, int numTiles) {
        GL20.glUseProgram(histProgram);
        if (histShiftLoc    >= 0) GL30.glUniform1ui(histShiftLoc, shift);
        if (histNumTilesLoc >= 0) GL30.glUniform1ui(histNumTilesLoc, numTiles);
    }

    public void useScan(int numTiles) {
        GL20.glUseProgram(scanProgram);
        if (scanNumTilesLoc >= 0) GL30.glUniform1ui(scanNumTilesLoc, numTiles);
    }

    public void useScatter(int shift, int numTiles) {
        GL20.glUseProgram(scatterProgram);
        if (scatterShiftLoc    >= 0) GL30.glUniform1ui(scatterShiftLoc, shift);
        if (scatterNumTilesLoc >= 0) GL30.glUniform1ui(scatterNumTilesLoc, numTiles);
    }

    public void useBitonic() {
        GL20.glUseProgram(bitonicProgram);
    }

    public void close() {
        if (histProgram    != 0) { GL20.glDeleteProgram(histProgram);    histProgram    = 0; }
        if (scanProgram    != 0) { GL20.glDeleteProgram(scanProgram);    scanProgram    = 0; }
        if (scatterProgram != 0) { GL20.glDeleteProgram(scatterProgram); scatterProgram = 0; }
        if (bitonicProgram != 0) { GL20.glDeleteProgram(bitonicProgram); bitonicProgram = 0; }
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(GolemCore.MOD_ID, "shaders/core/" + name);
    }

    private static int compileProgram(Identifier id) throws IOException {
        String source = readResource(id);
        int shader = GL43.glCreateShader(GL43.GL_COMPUTE_SHADER);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new RuntimeException(id.getPath() + " compile failed: " + log);
        }
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, shader);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(shader);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new RuntimeException(id.getPath() + " link failed: " + log);
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
