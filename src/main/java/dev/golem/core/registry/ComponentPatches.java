//? if >=1.20.5 {
package dev.golem.core.registry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.ItemLike;

/**
 * Default-component patches from registrars and item helpers, resolved only when the loader builds components.
 */
public final class ComponentPatches {

    private ComponentPatches() {
    }

    private static final List<Map.Entry<Supplier<? extends ItemLike>, Consumer<DataComponentMap.Builder>>> PATCHES =
            new CopyOnWriteArrayList<>();

    public static void add(Supplier<? extends ItemLike> item, Consumer<DataComponentMap.Builder> patch) {
        PATCHES.add(Map.entry(item, patch));
    }

    public static List<Map.Entry<Supplier<? extends ItemLike>, Consumer<DataComponentMap.Builder>>> all() {
        return List.copyOf(PATCHES);
    }
}
//?}
