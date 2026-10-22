package com.wf.gemrender.light;

import com.wf.gemrender.render.TextureUnits;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL30C.GL_RG32UI;
import static org.lwjgl.opengl.GL30C.GL_RG_INTEGER;

/**
 * Per-section 64-bit light masks, {@code GRID^3} sections toroidal around the camera
 * ({@code cell = section & (GRID - 1)}). Fragments beyond the window alias a near cell: extra loop
 * iterations, never wrong light (exact range + cone in the shader).
 */
final class LightGrid {
    static final int GRID = 32;

    static final int UNIT = TextureUnits.LIGHT_GRID;

    private static final int HALF = GRID / 2;
    private static final float CELL_RADIUS = 8.0f * (float) Math.sqrt(3.0);

    private final IntBuffer cells = MemoryUtil.memCallocInt(GRID * GRID * GRID * 2);

    private int textureId;

    /** Absolute section boxes {min xyz, max xyz}; empty = min > max. */
    private final int[] previous = {1, 1, 1, 0, 0, 0};
    private final int[] current = {1, 1, 1, 0, 0, 0};

    private int cellTests;
    private int cellsLit;

    void begin() {
        clear(previous);
        System.arraycopy(current, 0, previous, 0, 6);
        current[0] = current[1] = current[2] = 1;
        current[3] = current[4] = current[5] = 0;
        cellTests = 0;
        cellsLit = 0;
    }

    private void clear(int[] box) {
        for (int z = box[2]; z <= box[5]; z++) {
            for (int y = box[1]; y <= box[4]; y++) {
                for (int x = box[0]; x <= box[3]; x++) {
                    int i = index(x, y, z);
                    cells.put(i, 0);
                    cells.put(i + 1, 0);
                }
            }
        }
    }

    private static int index(int sx, int sy, int sz) {
        return ((((sz & (GRID - 1)) * GRID + (sy & (GRID - 1))) * GRID) + (sx & (GRID - 1))) * 2;
    }

    /**
     * Light {@code slot}'s bound sphere (camera-relative) over the window; each cell's bounding sphere
     * tested against the light sphere (point) or sphere + cone (spot).
     */
    void add(int slot, LightFrame.Light light, int camSx, int camSy, int camSz, double camX, double camY,
             double camZ) {
        int[] lo = new int[3];
        int[] hi = new int[3];
        float[] centre = {light.boundX, light.boundY, light.boundZ};
        double[] cam = {camX, camY, camZ};
        int[] camS = {camSx, camSy, camSz};
        for (int a = 0; a < 3; a++) {
            lo[a] = Math.max(camS[a] - HALF, (int) Math.floor((cam[a] + centre[a] - light.boundR) / 16.0));
            hi[a] = Math.min(camS[a] + HALF - 1, (int) Math.floor((cam[a] + centre[a] + light.boundR) / 16.0));
            if (lo[a] > hi[a]) {
                return;
            }
        }

        int word = slot >> 5;
        int bit = 1 << (slot & 31);
        float reach = light.range + CELL_RADIUS;
        float sinOuter = (float) Math.sqrt(1.0 - light.cosOuter * light.cosOuter);
        for (int z = lo[2]; z <= hi[2]; z++) {
            float cz = (float) (z * 16.0 + 8.0 - camZ) - light.rz;
            for (int y = lo[1]; y <= hi[1]; y++) {
                float cy = (float) (y * 16.0 + 8.0 - camY) - light.ry;
                for (int x = lo[0]; x <= hi[0]; x++) {
                    float cx = (float) (x * 16.0 + 8.0 - camX) - light.rx;
                    cellTests++;
                    float d2 = cx * cx + cy * cy + cz * cz;
                    if (d2 >= reach * reach) {
                        continue;
                    }
                    if (light.spot && !coneHitsSphere(light, sinOuter, cx, cy, cz, d2)) {
                        continue;
                    }
                    int i = index(x, y, z) + word;
                    cells.put(i, cells.get(i) | bit);
                    cellsLit++;
                    grow(x, y, z);
                }
            }
        }
    }

    /** Sphere (cell, radius CELL_RADIUS, offset v from apex) vs cone of half angle outer, length range. */
    private static boolean coneHitsSphere(LightFrame.Light light, float sinOuter, float vx, float vy, float vz,
                                          float d2) {
        float axial = vx * light.dx + vy * light.dy + vz * light.dz;
        float closest = light.cosOuter * (float) Math.sqrt(Math.max(0.0f, d2 - axial * axial)) - axial * sinOuter;
        return closest <= CELL_RADIUS && axial <= light.range + CELL_RADIUS && axial >= -CELL_RADIUS;
    }

    private void grow(int x, int y, int z) {
        if (current[0] > current[3]) {
            current[0] = current[3] = x;
            current[1] = current[4] = y;
            current[2] = current[5] = z;
            return;
        }
        current[0] = Math.min(current[0], x);
        current[1] = Math.min(current[1], y);
        current[2] = Math.min(current[2], z);
        current[3] = Math.max(current[3], x);
        current[4] = Math.max(current[4], y);
        current[5] = Math.max(current[5], z);
    }

    void upload() {
        int previousUnit = TextureUnits.activate(UNIT);
        if (textureId == 0) {
            textureId = glGenTextures();
            glBindTexture(GL_TEXTURE_3D, textureId);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAX_LEVEL, 0);
            glTexImage3D(GL_TEXTURE_3D, 0, GL_RG32UI, GRID, GRID, GRID, 0, GL_RG_INTEGER, GL_UNSIGNED_INT, cells);
        } else {
            glBindTexture(GL_TEXTURE_3D, textureId);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, GRID);
            glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, GRID);
            uploadBox(previous);
            uploadBox(current);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_SKIP_IMAGES, 0);
        }
        TextureUnits.restore(previousUnit);
    }

    void bind() {
        int previousUnit = TextureUnits.activate(UNIT);
        glBindTexture(GL_TEXTURE_3D, textureId);
        TextureUnits.restore(previousUnit);
    }

    /** Box spans <= GRID per axis => <= 2 wrapped runs per axis. */
    private void uploadBox(int[] box) {
        if (box[0] > box[3]) {
            return;
        }
        int[][] runs = new int[3][];
        for (int a = 0; a < 3; a++) {
            int start = box[a] & (GRID - 1);
            int length = box[a + 3] - box[a] + 1;
            runs[a] = start + length <= GRID ? new int[] {start, length}
                    : new int[] {start, GRID - start, 0, length - (GRID - start)};
        }
        for (int iz = 0; iz < runs[2].length; iz += 2) {
            for (int iy = 0; iy < runs[1].length; iy += 2) {
                for (int ix = 0; ix < runs[0].length; ix += 2) {
                    glPixelStorei(GL_UNPACK_SKIP_PIXELS, runs[0][ix]);
                    glPixelStorei(GL_UNPACK_SKIP_ROWS, runs[1][iy]);
                    glPixelStorei(GL_UNPACK_SKIP_IMAGES, runs[2][iz]);
                    glTexSubImage3D(GL_TEXTURE_3D, 0, runs[0][ix], runs[1][iy], runs[2][iz], runs[0][ix + 1],
                            runs[1][iy + 1], runs[2][iz + 1], GL_RG_INTEGER, GL_UNSIGNED_INT, cells);
                }
            }
        }
    }

    int cellTests() {
        return cellTests;
    }

    int cellsLit() {
        return cellsLit;
    }
}
