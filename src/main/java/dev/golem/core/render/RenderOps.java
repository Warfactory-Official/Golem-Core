package dev.golem.core.render;

//? if >=1.21.11 {
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlRenderPass;
import net.minecraft.client.renderer.SkyRenderer;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Typed reads of native renderer and GUI state. */
public final class RenderOps {
    private RenderOps() {}

    public static List<GuiMessage> chatMessages(ChatComponent chat) {
        return chat.allMessages;
    }

    public static Map<UUID, LerpingBossEvent> bossEvents(BossHealthOverlay overlay) {
        return overlay.events;
    }

    //? if <26.1 {
    /*/^* The most free section buffers seen: the pool's size, which 1.21.11 does not keep. ^/
    private static int poolSize;
    *///?}

    /** Includes discovery, propagation, and borrowed buffers that the compile queue alone omits. */
    public static @Nullable String terrainBusy(Minecraft client) {
        var renderer = client.levelRenderer;
        var dispatcher = renderer.getSectionRenderDispatcher();
        if (dispatcher == null) return "no dispatcher";
        var graph = renderer.getSectionOcclusionGraph();
        var task = graph.fullUpdateTask;
        var state = graph.currentGraph.get();
        if (graph.needsFullUpdate || task != null && !task.isDone() || state == null) return "graph rebuild";
        //? if >=26.2 {
        /*if (!state.sectionsToPropagateFrom().isEmpty()) return "graph propagation";
        *///?} else {
        if (!state.events().sectionsToPropagateFrom().isEmpty()) return "graph propagation";
        //?}
        if (graph.needsFrustumUpdate.get()) return "frustum";
        if (!dispatcher.isQueueEmpty()) return "compile queue";
        //? if >=26.1 {
        var inFlight = client.renderBuffers().sectionBufferPool().freeBuffers.remainingCapacity();
        //?} else {
        /*int free = client.renderBuffers().sectionBufferPool().getFreeBufferCount();
        poolSize = Math.max(poolSize, free);
        int inFlight = poolSize - free;
        *///?}
        return inFlight == 0 ? null : inFlight + " compiles in flight";
    }

    public static @Nullable SkyRenderer skyRenderer(LevelRenderer renderer) {
        return renderer.skyRenderer;
    }

    public static GpuBuffer starBuffer(SkyRenderer renderer) {
        return renderer.starBuffer;
    }

    /** Native OpenGL buffer name; the caller retains the buffer's context and lifetime. */
    public static int bufferHandle(GlBuffer buffer) {
        return buffer.handle;
    }

    public static boolean validationEnabled() {
        //? if >=26.3 {
        /*return false;
        *///?} else {
        return GlRenderPass.VALIDATION;
        //?}
    }

}
//?}
