package dev.golem.core.flywheel.instance;

//? if >=26.1 {

import org.joml.Matrix4fc;

final class InstanceHandle implements Instance {

    private final AbstractInstancer instancer;
    private int index;
    private boolean deleted;

    InstanceHandle(AbstractInstancer instancer, int index) {
        this.instancer = instancer;
        this.index = index;
    }

    int index() {
        return index;
    }

    void moveTo(int index) {
        this.index = index;
    }

    AbstractInstancer instancer() {
        return instancer;
    }

    @Override
    public void setTransform(Matrix4fc transform) {
        if (deleted) return;
        instancer.writeTransform(index, transform);
    }

    @Override
    public void setLight(int packedLight) {
        if (deleted) return;
        instancer.writeLight(index, packedLight);
    }

    @Override
    public void setOverlay(int packedOverlay) {
        if (deleted) return;
        instancer.writeOverlay(index, packedOverlay);
    }

    @Override
    public void setColor(int argb) {
        if (deleted) return;
        instancer.writeColor(index, argb);
    }

    @Override
    public void setRawColor(float r, float g, float b, float a) {
        if (deleted) return;
        instancer.writeRawColor(index, r, g, b, a);
    }

    @Override
    public void setVisible(boolean visible) {
        if (deleted) return;
        instancer.writeVisible(index, visible);
    }

    @Override
    public void setUV(float offsetU, float offsetV, float scaleU, float scaleV) {
        if (deleted) return;
        instancer.writeUV(index, offsetU, offsetV, scaleU, scaleV);
    }

    @Override
    public void delete() {
        if (deleted) return;
        deleted = true;
        instancer.free(this);
    }

    @Override
    public int meshHandle() {
        return instancer.meshHandle();
    }
}
//?}
