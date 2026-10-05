package dev.golem.core.gametest;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * The plugin of {@code golemcore.fabric.mixins.json}: adds {@code MixinFabricGameTestModInitializer} only when its target
 * is in the game. Fabric's GameTest module ({@code fabric-gametest-api-v1}) is a dev-run module that the released Fabric
 * API jar does not contain, and the config is {@code required}, so naming the mixin unconditionally failed the whole
 * config at PREPARE and crashed every player's client at startup. Asked of the class path (as {@code SableMixinPlugin}
 * does): the target's class file, and the mixin's own (it exists only on the nodes whose Fabric API it is written for).
 * Lives outside the mixin package, which Mixin refuses to class-load from.
 */
public final class GameTestMixinPlugin implements IMixinConfigPlugin {

    private static final String TARGET = "net/fabricmc/fabric/impl/gametest/FabricGameTestModInitializer.class";
    private static final String MIXIN = "MixinFabricGameTestModInitializer";

    private List<String> extra = List.of();

    @Override
    public void onLoad(String mixinPackage) {
        ClassLoader loader = GameTestMixinPlugin.class.getClassLoader();
        String mixinFile = mixinPackage.replace('.', '/') + "/" + MIXIN + ".class";
        if (loader.getResource(TARGET) != null && loader.getResource(mixinFile) != null) extra = List.of(MIXIN);
    }

    @Override public List<String> getMixins() { return extra; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
