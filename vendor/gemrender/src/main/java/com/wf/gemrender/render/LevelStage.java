package com.wf.gemrender.render;

//? if >=26.1 {
/*import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
*///?} else {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
//?}

public final class LevelStage {

    //? if >=26.1 {
    /*private final ChunkSectionsToRender chunkSections;

    public LevelStage(ChunkSectionsToRender chunkSections) {
        this.chunkSections = chunkSections;
    }

    public ChunkSectionsToRender chunkSections() {
        return chunkSections;
    }
    *///?} else {
    private final LevelRenderer levelRenderer;

    private final Camera camera;

    private final Frustum frustum;

    private final PoseStack poseStack;

    private final Matrix4f modelViewMatrix;

    private final Matrix4f projectionMatrix;

    public LevelStage(LevelRenderer levelRenderer, Camera camera, Frustum frustum, PoseStack poseStack,
                      Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        this.levelRenderer = levelRenderer;
        this.camera = camera;
        this.frustum = frustum;
        this.poseStack = poseStack;
        this.modelViewMatrix = modelViewMatrix;
        this.projectionMatrix = projectionMatrix;
    }

    public LevelRenderer levelRenderer() {
        return levelRenderer;
    }

    public Camera camera() {
        return camera;
    }

    public Frustum frustum() {
        return frustum;
    }

    public PoseStack poseStack() {
        return poseStack;
    }

    public Matrix4f modelViewMatrix() {
        return modelViewMatrix;
    }

    public Matrix4f projectionMatrix() {
        return projectionMatrix;
    }
    //?}
}
