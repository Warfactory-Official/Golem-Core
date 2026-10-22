package dev.golem.core.gemrender.mixin;

import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies the GemRender patches only to the GemRender release they fix, read from the metadata of the jar that holds
 * the patched class (the loaders' mod lists are not public this early). Any other version, or no GemRender, is left alone.
 */
public final class GemRenderMixinPlugin implements IMixinConfigPlugin {

    public static final String PATCHED_VERSION = "0.1.14";
    private static final String TARGET = "dev/engine_room/flywheel/impl/event/RenderContextImpl.class";
    private static final Pattern VERSION = Pattern.compile("gemrender\"[\\s\\S]*?\"?version\"?\\s*[=:]\\s*\"([^\"]+)\"");

    private boolean patched;

    @Override
    public void onLoad(String mixinPackage) {
        String version = versionOf(TARGET);
        patched = isPatched(version);
        if (version != null) {
            LogManager.getLogger("golemcore").info("GemRender {}: {} the {} view-bob patch", version,
                    patched ? "applying" : "skipping", PATCHED_VERSION);
        }
    }

    /** Whether {@code version} is the GemRender release Core patches. */
    public static boolean isPatched(String version) {
        return version != null && (version.equals(PATCHED_VERSION) || version.startsWith(PATCHED_VERSION + "+"));
    }

    /** GemRender's version, from the metadata of the jar that holds {@code classResource}; null without that class. */
    static String versionOf(String classResource) {
        URL target = GemRenderMixinPlugin.class.getClassLoader().getResource(classResource);
        if (target == null) return null;
        String jar = target.toString().substring(0, target.toString().length() - classResource.length());
        for (String metadata : new String[] {"META-INF/neoforge.mods.toml", "META-INF/mods.toml", "fabric.mod.json"}) {
            try (InputStream in = URI.create(jar + metadata).toURL().openStream()) {
                Matcher m = VERSION.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                if (m.find()) return m.group(1);
            } catch (IOException | IllegalArgumentException missing) {
                // this loader's metadata file is not in the jar; try the other
            }
        }
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return patched;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
