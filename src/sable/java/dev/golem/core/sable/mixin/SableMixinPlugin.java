package dev.golem.core.sable.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the Sable mixins only in a game that has Sable; without it their target class does not exist. Asked of the
 * class path (whether Sable's class file is there) rather than of a loader's mod list, so the one config serves NeoForge
 * and Fabric: neither loader's mod list is a public, stable API this early, and the class file is what the mixin needs.
 */
public final class SableMixinPlugin implements IMixinConfigPlugin {

    private static final String TARGET = "dev/ryanhcode/sable/api/SubLevelAssemblyHelper.class";

    private boolean sable;

    @Override
    public void onLoad(String mixinPackage) {
        sable = SableMixinPlugin.class.getClassLoader().getResource(TARGET) != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return sable;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
