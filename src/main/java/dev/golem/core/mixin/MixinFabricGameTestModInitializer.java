package dev.golem.core.mixin;

// Fabric API's modern GameTest entrypoint discovers annotated methods, but exposes no public hook
// for the data-driven TEST_INSTANCE registry. This is the smallest hook available: it forwards the
// registry-load list into Golem Core, which owns the loader-neutral test declarations. The vanilla
// registry and loader APIs were checked first; neither can register these dynamic instances from a
// Fabric mod initializer, so this exact callback is the remaining mixin seam.
//? if fabric && >=1.21.11 {
/*import java.util.List;
import net.fabricmc.fabric.impl.gametest.FabricGameTestModInitializer;
//? if >=26.1 {
import net.minecraft.resources.RegistryLoadTask;
//?} else {
/^import net.minecraft.resources.RegistryDataLoader;
^///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FabricGameTestModInitializer.class)
public final class MixinFabricGameTestModInitializer {

    //? if >=26.1 {
    @Inject(method = "registerDynamicEntries", at = @At("TAIL"))
    private static void golem$registerDynamicEntries(List<RegistryLoadTask<?>> loadTasks, CallbackInfo callback) {
        dev.golem.core.gametest.GameTests.registerFabricDynamicEntries(loadTasks);
    }
    //?} else {
    /^@Inject(method = "registerDynamicEntries", at = @At("TAIL"))
    private static void golem$registerDynamicEntries(List<RegistryDataLoader.Loader<?>> loaders, CallbackInfo callback) {
        dev.golem.core.gametest.GameTests.registerFabricDynamicEntries(loaders);
    }
    ^///?}
}
*///?}
