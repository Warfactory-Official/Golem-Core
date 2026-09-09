package dev.golem.core.flywheel;

//? if neoforge && >=26.1 {

import java.nio.ByteBuffer;
import net.minecraft.util.Mth;

final class Layout {

    // pos3f (12) + uv2f (8) + normal 3b + 1 pad (4) = 24
    static final int STRIDE = 24;

    private Layout() {
    }

    static void put(ByteBuffer buffer, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        buffer.putFloat(x);
        buffer.putFloat(y);
        buffer.putFloat(z);
        buffer.putFloat(u);
        buffer.putFloat(v);
        buffer.put(packNormal(nx));
        buffer.put(packNormal(ny));
        buffer.put(packNormal(nz));
        buffer.put((byte) 0);
    }

    private static byte packNormal(float value) {
        return (byte) ((int) (Mth.clamp(value, -1.0F, 1.0F) * 127.0F) & 0xFF);
    }
}
//?}
