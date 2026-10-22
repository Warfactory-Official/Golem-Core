package dev.golem.core.render;

/** Rows of camera-facing quads, one position, half size, colour and light each, sorted into texture layers. */
public final class Billboards {

    @FunctionalInterface
    public interface RowWriter {
        void row(double x, double y, double z, float halfSize,
                 float red, float green, float blue, float alpha, int packedLight);

        default void sprite(double x, double y, double z, float halfSize,
                            float red, float green, float blue, float alpha, int packedLight,
                            int column, int row, int columns, int rows) {
            row(x, y, z, halfSize, red, green, blue, alpha, packedLight);
        }
    }

    @FunctionalInterface
    public interface LayerWriter {
        RowWriter layer(int index);
    }

    private Billboards() {}
}
