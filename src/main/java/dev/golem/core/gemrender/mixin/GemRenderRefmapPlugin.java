package dev.golem.core.gemrender.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.MixinEnvironment.Option;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.refmap.IReferenceMapper;
import org.spongepowered.asm.mixin.refmap.ReferenceMapper;
import org.spongepowered.asm.mixin.refmap.RemappingReferenceMapper;
import org.spongepowered.asm.mixin.transformer.Config;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/**
 * 1.20.1 Forge only. GemRender 0.1.14 ships {@code gemrender.refmap.json} but its two configs never name it, so outside a
 * dev run its mixins cannot find their SRG targets and the game crashes. Once every config is selected and before any
 * mixin applies, this gives those configs the reference mapper Mixin's own {@code onSelect} would have built from that
 * file. Applied only to 0.1.14; remove it once GemRender names its refmap.
 *
 * <p>Sets one private Mixin field by reflection, a version-gated exception to AGENTS.md section 7: Mixin has no public way
 * to give another config a refmap.
 */
public final class GemRenderRefmapPlugin implements IMixinConfigPlugin {

    private static final Logger LOG = LogManager.getLogger("golemcore");
    private static final String GEM_CLASS = "com/wf/gemrender/mixin/DrawManagerMixin.class";
    private static final String REFMAP = "gemrender.refmap.json";
    private static final String[] CONFIGS = {"gemrender.mixins.json", "gemrender.direct.mixins.json"};

    private boolean patched;

    @Override
    public void onLoad(String mixinPackage) {
        String version = GemRenderMixinPlugin.versionOf(GEM_CLASS);
        patched = GemRenderMixinPlugin.isPatched(version);
        if (version != null) {
            LOG.info("GemRender {}: {} the {} refmap patch", version, patched ? "applying" : "skipping",
                    GemRenderMixinPlugin.PATCHED_VERSION);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        if (!patched) return;
        for (String name : CONFIGS) {
            Config handle = Config.create(name, MixinEnvironment.getDefaultEnvironment());
            if (handle == null) continue;
            IMixinConfig config = handle.getConfig();
            MixinEnvironment env = config.getEnvironment();
            IReferenceMapper mapper = ReferenceMapper.read(REFMAP);
            if (mapper.isDefault()) {
                LOG.warn("GemRender refmap patch: {} could not be read, {} left as it is", REFMAP, name);
                continue;
            }
            if (env.getOption(Option.REFMAP_REMAP)) mapper = RemappingReferenceMapper.of(env, mapper);
            try {
                set(config, "refMapperConfig", REFMAP);
                set(config, "refMapper", mapper);
                LOG.info("GemRender refmap patch: {} now reads {}", name, REFMAP);
            } catch (ReflectiveOperationException | RuntimeException failed) {
                LOG.error("GemRender refmap patch: could not set the reference map of {}", name, failed);
            }
        }
    }

    private static void set(Object config, String field, Object value) throws ReflectiveOperationException {
        Field f = config.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(config, value);
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return false;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
