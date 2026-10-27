//? if >=1.20.5 {
package dev.golem.core.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.ItemLike;

/**
 * Default-component patches queued during declarations, applied once per loader when it builds item components
 * (upstream hbm-space's static patch list). Loader shims drain the queue; mods only append through
 * {@link Registrar#modifyDefaultComponents}. Items stay handles until the event fires: resolving them at
 * declaration time crashes, the vanilla registry does not have them yet.
 */
public final class ComponentPatches {

    private ComponentPatches() {}

    private static final List<Map.Entry<Supplier<? extends ItemLike>, Consumer<DataComponentMap.Builder>>> PATCHES =
            new ArrayList<>();

    static void add(Supplier<? extends ItemLike> item, Consumer<DataComponentMap.Builder> patch) {
        PATCHES.add(Map.entry(item, patch));
    }

    public static List<Map.Entry<Supplier<? extends ItemLike>, Consumer<DataComponentMap.Builder>>> all() {
        return List.copyOf(PATCHES);
    }
}
//?}
