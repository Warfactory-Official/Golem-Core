package dev.golem.core.mixin;

//? if fabric && <1.20.5 {
/*import net.fabricmc.fabric.api.resource.ModResourcePack;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.minecraft.server.packs.PackType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/^*
 * Mod resource packs dependencies-first, so a dependent's same-path file wins (the group pack resolves last-first).
 * This fabric-api orders by {@code getAllMods()}; Forge-like loaders and later fabric-api already sort this way.
 ^/
@Mixin(targets = "net.fabricmc.fabric.impl.resource.loader.ModResourcePackUtil", remap = false)
public abstract class MixinModResourcePackOrder {

    @Inject(method = "appendModResourcePacks", at = @At("RETURN"), remap = false)
    private static void golem$dependenciesFirst(List<ModResourcePack> packs, PackType type, String subPath, CallbackInfo ci) {
        Map<String, ModResourcePack> byId = new LinkedHashMap<>();
        for (ModResourcePack pack : packs) byId.put(pack.getFabricModMetadata().getId(), pack);
        List<ModResourcePack> ordered = new ArrayList<>(packs.size());
        Set<String> done = new HashSet<>();
        for (String id : byId.keySet()) golem$visit(id, byId, done, new HashSet<>(), ordered);
        packs.clear();
        packs.addAll(ordered);
    }

    private static void golem$visit(String id, Map<String, ModResourcePack> byId, Set<String> done, Set<String> path,
            List<ModResourcePack> ordered) {
        if (done.contains(id) || !path.add(id)) return;
        ModResourcePack pack = byId.get(id);
        for (ModDependency dependency : pack.getFabricModMetadata().getDependencies()) {
            if (dependency.getKind() == ModDependency.Kind.DEPENDS && byId.containsKey(dependency.getModId())) {
                golem$visit(dependency.getModId(), byId, done, path, ordered);
            }
        }
        done.add(id);
        ordered.add(pack);
    }
}
*///?}
