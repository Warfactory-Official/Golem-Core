package dev.golem.core.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
//? if >=26.1 {
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}

/** Frame-scoped geometry drawn after world translucency, with the submitting renderer's exact pose. */
public final class WorldEffects {
    private record Entry(int order, PoseStack pose, Matrix4f modelView, Runnable drawState, RenderType type,
                         SubmitSink.CustomGeometry geometry) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static ClientLevel level;
    private static boolean collecting;
    private static boolean registered;
    private static boolean reported;
    private static com.mojang.blaze3d.pipeline.TextureTarget worldDepth;
    private static boolean depthCaptured;
    private static final List<Consumer<CameraInfo>> BEGIN_HOOKS = new ArrayList<>();

    private WorldEffects() {}

    public static void init() {
        if (registered) return;
        registered = true;
        dev.golem.core.client.ClientResourceReloads.register("golemcore", "world_effects", () -> {
            clear();
            if (worldDepth != null) worldDepth.destroyBuffers();
            worldDepth = null;
            DepthMerge.close();
        });
        // 26.1 has no loader callback after the transparency framegraph has been composed.
        //? if >=26.1 {
        //? if fabric {
        /*net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN
                .register(context -> captureDepth());
        *///?} elif neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterTranslucentBlocks event) -> captureDepth());
        //?}
        //?} else {
        /*//? if fabric {
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(context -> begin());
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> captureDepth());
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST.register(context -> end());
        //?} elif neoforge {
        /^net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent event) -> {
                    if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY) begin();
                    if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) captureDepth();
                    if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL) end();
                });
        ^///?} elif forge {
        /^net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.RenderLevelStageEvent event) -> {
                    if (event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY) begin();
                    if (event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) captureDepth();
                    if (event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL) end();
                });
        ^///?}
        *///?}
    }

    /** Save real world depth before the Fabulous fullscreen combine clears/overwrites main depth. */
    private static void captureDepth() {
        if (!isCollecting() || ENTRIES.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        var source = minecraft.getMainRenderTarget();
        //? if <26.1 {
        /*int readTarget = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawTarget = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        *///?}
        try {
            if (worldDepth == null || worldDepth.width != source.width || worldDepth.height != source.height) {
                if (worldDepth != null) worldDepth.destroyBuffers();
                //? if >=26.1 {
                worldDepth = new com.mojang.blaze3d.pipeline.TextureTarget("golem_world_effect_depth", source.width, source.height, true);
                //?} else {
                /*worldDepth = new com.mojang.blaze3d.pipeline.TextureTarget(source.width, source.height, true, false);
                *///?}
            }
            // Entity translucency has its own depth attachment in Fabulous. A terrain-only copy
            // loses the player/armor silhouettes; overwriting it with entity depth loses water.
            DepthMerge.merge(worldDepth, source, minecraft.levelRenderer.getTranslucentTarget(),
                    minecraft.levelRenderer.getItemEntityTarget());
            depthCaptured = true;
        } finally {
            //? if <26.1 {
            /*com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, readTarget);
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
            *///?}
        }
    }

    public static void begin() {
        clear();
        level = Minecraft.getInstance().level;
        collecting = level != null;
        if (collecting) for (Consumer<CameraInfo> hook : List.copyOf(BEGIN_HOOKS)) hook.accept(CameraInfo.current());
    }

    public static boolean isCollecting() {
        return collecting && level == Minecraft.getInstance().level;
    }

    /** Registers client geometry that must be submitted at the start of the world frame. */
    public static void onBegin(Consumer<CameraInfo> callback) {
        BEGIN_HOOKS.add(callback);
    }

    /** Submission order is stable within a priority; GUI/hand previews are never carried into a world frame. */
    public static void submit(PoseStack pose, RenderType type, int order, SubmitSink.CustomGeometry geometry) {
        if (!isCollecting()) return;
        PoseStack copy = new PoseStack();
        copy.last().pose().set(pose.last().pose());
        copy.last().normal().set(pose.last().normal());
        ENTRIES.add(new Entry(order, copy, new Matrix4f(RenderSystem.getModelViewMatrix()), captureDrawState(), type, geometry));
    }

    private static Runnable captureDrawState() {
        //? if >=26.1 {
        var projection = RenderSystem.getProjectionMatrixBuffer();
        var projectionType = RenderSystem.getProjectionType();
        var fog = RenderSystem.getShaderFog();
        return () -> {
            RenderSystem.setProjectionMatrix(projection, projectionType);
            RenderSystem.setShaderFog(fog);
        };
        //?} else {
        /*Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        var fogShape = RenderSystem.getShaderFogShape();
        float[] fogColor = RenderSystem.getShaderFogColor().clone();
        float[] color = RenderSystem.getShaderColor().clone();
        return () -> {
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
            RenderSystem.setShaderFogColor(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        };
        *///?}
    }

    public static void clear() {
        ENTRIES.clear();
        collecting = false;
        depthCaptured = false;
        level = null;
    }

    /** Called before the world projection/depth are replaced for first-person hands or GUI rendering. */
    public static void end() {
        Runnable restoreState = null;
        try {
            if (!isCollecting()) return;
            if (ENTRIES.isEmpty()) return;
            collecting = false;
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            buffers.endBatch();
            if (!depthCaptured) throw new IllegalStateException("World effects submitted without world depth capture");
            Minecraft.getInstance().getMainRenderTarget().copyDepthFrom(worldDepth);
            //? if <26.1 {
            /*Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            *///?}
            ENTRIES.sort(Comparator.comparingInt(Entry::order));
            restoreState = captureDrawState();
            boolean captured = false;
            for (Entry entry : ENTRIES) {
                if (!captured && HeatDistortionRenderType.isSceneSampling(entry.type())) {
                    HeatDistortionRenderType.prepareSceneForPass();
                    captured = true;
                }
                entry.drawState().run();
                // Keep the world view even when a loader's last-world event restores its global stack.
                //? if >=1.21 {
                RenderSystem.getModelViewStack().pushMatrix().set(entry.modelView());
                //? if <26.1 {
                /*RenderSystem.applyModelViewMatrix();
                *///?}
                //?} else {
                /*RenderSystem.getModelViewStack().pushPose();
                RenderSystem.getModelViewStack().last().pose().set(entry.modelView());
                RenderSystem.applyModelViewMatrix();
                *///?}
                try {
                    entry.geometry().render(entry.pose().last(), buffers.getBuffer(entry.type()));
                    buffers.endBatch();
                } finally {
                    //? if >=1.21 {
                    RenderSystem.getModelViewStack().popMatrix();
                    //? if <26.1 {
                    /*RenderSystem.applyModelViewMatrix();
                    *///?}
                    //?} else {
                    /*RenderSystem.getModelViewStack().popPose();
                    RenderSystem.applyModelViewMatrix();
                    *///?}
                }
            }
            if (!reported) {
                reported = true;
                dev.golem.core.GolemCore.LOG.info("World effects rendered after translucent composition ({} submissions)", ENTRIES.size());
            }
        } finally {
            try {
                if (restoreState != null) restoreState.run();
            } finally {
                clear();
            }
        }
    }
}
