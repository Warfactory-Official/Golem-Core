package dev.golem.core.render;

//? if >=1.21 {
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
//? if >=26.2 {
/*import com.mojang.blaze3d.vertex.ByteBufferBuilder;
*///?} else {
import com.mojang.blaze3d.vertex.Tesselator;
//?}

/** Vanilla's shared immediate-mode buffer: a mesh must be drawn before the next {@link #begin}. */
public final class CompatTesselator {

    //? if >=26.2 {
    /*private static final ByteBufferBuilder BUFFER = new ByteBufferBuilder(786432);
    *///?}

    private CompatTesselator() {}

    public static BufferBuilder begin(VertexFormat.Mode mode, VertexFormat format) {
        //? if >=26.2 {
        /*return new BufferBuilder(BUFFER, mode, format);
        *///?} else {
        return Tesselator.getInstance().begin(mode, format);
        //?}
    }
}
//?} else {
/*/^* 1.20.1 builds meshes on the Tesselator's own builder. ^/
public final class CompatTesselator {
    private CompatTesselator() {}
}
*///?}
