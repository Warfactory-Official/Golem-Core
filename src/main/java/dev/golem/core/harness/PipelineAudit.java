package dev.golem.core.harness;

//? if >=1.21.11 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.golem.core.GolemCore;
import dev.golem.core.client.ClientTicks;
import dev.golem.core.platform.Platform;
import net.minecraft.client.Minecraft;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Development: {@code -Dgolem.pipelineAudit=true} compiles every static pipeline of Core's render classes once a
 * world is loaded (shaders are reloaded by then) and logs each the device rejects, so an error shows without drawing.
 */
final class PipelineAudit {

    private static boolean done;

    private PipelineAudit() {}

    static void installIfRequested() {
        if (!Boolean.getBoolean("golem.pipelineAudit") || !Platform.isDevelopment()) return;
        ClientTicks.onEndTick(() -> {
            if (done || Minecraft.getInstance().level == null) return;
            done = true;
            List<RenderPipeline> pipelines = new ArrayList<>();
            for (String name : renderClasses()) collect(name, pipelines);
            int failed = 0;
            for (RenderPipeline pipeline : pipelines) {
                //? if >=26.3 {
                /*boolean valid = RenderSystem.getCompiledPipelineNullable(pipeline) != null;
                *///?} else {
                boolean valid = RenderSystem.getDevice().precompilePipeline(pipeline).isValid();
                //?}
                if (!valid) {
                    failed++;
                    GolemCore.LOG.error("[pipeline audit] {} failed to compile", pipeline.getLocation());
                }
            }
            GolemCore.LOG.info("[pipeline audit] {} pipeline(s), {} failed", pipelines.size(), failed);
        });
    }

    private static void collect(String className, List<RenderPipeline> out) {
        try {
            Class<?> type = Class.forName(className, true, PipelineAudit.class.getClassLoader());
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != RenderPipeline.class) continue;
                field.setAccessible(true);
                if (field.get(null) instanceof RenderPipeline pipeline && !out.contains(pipeline)) out.add(pipeline);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            GolemCore.LOG.error("[pipeline audit] could not read {}", className, e);
        }
    }

    private static List<String> renderClasses() {
        try {
            Path root = Path.of(GolemCore.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(root)) return classesUnder(root);
            try (FileSystem jar = FileSystems.newFileSystem(root, Map.of())) {
                return classesUnder(jar.getPath("/"));
            }
        } catch (IOException | URISyntaxException e) {
            GolemCore.LOG.error("[pipeline audit] could not list Core's classes", e);
            return List.of();
        }
    }

    private static List<String> classesUnder(Path root) throws IOException {
        Path render = root.resolve("dev/golem/core/render");
        if (!Files.isDirectory(render)) return List.of();
        try (Stream<Path> files = Files.walk(render)) {
            return files.map(root::relativize).map(Path::toString).map(p -> p.replace(File.separatorChar, '/'))
                    .filter(p -> p.endsWith(".class")).map(p -> p.substring(0, p.length() - 6).replace('/', '.')).toList();
        }
    }
}
//?} else {
/*final class PipelineAudit {
    private PipelineAudit() {}

    static void installIfRequested() {}
}
*///?}
