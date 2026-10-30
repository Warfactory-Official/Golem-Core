package dev.golem.core.rei;

//? if rei {

import dev.golem.core.data.GolemComponent;
import dev.golem.core.viewer.Subtypes;
import dev.golem.core.viewer.ViewerType;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.comparison.ComparisonContext;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The REI spellings of the neutral viewer declarations that both of a mod's REI plugins need: category ids and item
 * comparators (subtypes). Comparators are registered by the common plugin on 26.1 (REI 26's {@code REICommonPlugin})
 * and by the client plugin below it (REI 12/16 register them on any {@code REIPlugin}); see {@link ReiCommonPlugin}.
 */
public final class Rei {

    private static final Map<ViewerType<?>, CategoryIdentifier<?>> IDS = new ConcurrentHashMap<>();

    /** REI's own furnace category ({@code BuiltinPlugin.SMELTING}), named here so nothing links the default plugin. */
    static final CategoryIdentifier<?> SMELTING = CategoryIdentifier.of("minecraft", "plugins/smelting");

    private Rei() {}

    /** The REI category id of a viewer category: the same {@code namespace:path}. */
    @SuppressWarnings("unchecked")
    public static <T> CategoryIdentifier<ReiDisplay<T>> id(ViewerType<T> type) {
        return (CategoryIdentifier<ReiDisplay<T>>) IDS.computeIfAbsent(type,
                t -> CategoryIdentifier.of(t.namespace(), t.path()));
    }

    /**
     * Registers the declarations as item comparators: stacks of the item compare by the components' values (or the
     * interpreter's string), as JEI's subtypes do. The hash is FNV-1a over the same {@code |value} string Golem's JEI
     * subtype interpreter builds.
     */
    static void comparators(ItemComparatorRegistry registry, Subtypes.Declarations declarations) {
        declarations.register(new Subtypes() {
            @Override
            public void components(Item item, GolemComponent<?>... components) {
                if (components.length == 0) return;
                registry.register((context, stack) -> key(stack, components), item);
            }

            @Override
            public void listed(Item item, GolemComponent<?>... components) {
                if (components.length == 0) return;
                registry.register((context, stack) -> context == ComparisonContext.FUZZY ? 0 : key(stack, components), item);
            }

            @Override
            public void interpreter(Item item, Function<ItemStack, String> interpreter) {
                registry.register((context, stack) -> hash(interpreter.apply(stack)), item);
            }
        });
    }

    private static long key(ItemStack stack, GolemComponent<?>... components) {
        StringBuilder key = new StringBuilder();
        for (GolemComponent<?> component : components) {
            Object value = component.get(stack).orElse(null);
            key.append('|').append(value == null ? "" : value);
        }
        return hash(key);
    }

    private static long hash(CharSequence text) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
//?}
