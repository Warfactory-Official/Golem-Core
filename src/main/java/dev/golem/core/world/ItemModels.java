package dev.golem.core.world;

import dev.golem.core.Ids;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
 *///?}
//? if >=1.20.5 {
import dev.golem.core.registry.ComponentPatches;
import net.minecraft.core.component.DataComponents;
//?}

/** Item-definition aliases retain lazy handles until the registry and client models are available. */
public final class ItemModels {
    private record Alias(Supplier<? extends ItemLike> item, String namespace, String path) {
    }

    private static final List<Alias> ALIASES = new CopyOnWriteArrayList<>();
    //? if >=26.1 {
    private static volatile Map<Item, Identifier> resolved;
    //?} else {
    /*private static volatile Map<Item, ResourceLocation> resolved;
     *///?}

    private ItemModels() {
    }

    public static void alias(Supplier<? extends ItemLike> item, String namespace, String path) {
        ALIASES.add(new Alias(item, namespace, path));
        resolved = null;
        //? if >=1.21.2 {
        ComponentPatches.add(item, components -> components.set(DataComponents.ITEM_MODEL, Ids.of(namespace, path)));
        //?}
    }

    public static
        //? if >=26.1 {
    Identifier
    //?} else {
    /*ResourceLocation
     *///?}
    id(Item item) {
        var models = resolved;
        if (models == null) {
            //? if >=26.1 {
            var built = new IdentityHashMap<Item, Identifier>();
            //?} else {
            /*var built = new IdentityHashMap<Item, ResourceLocation>();
             *///?}
            for (var alias : ALIASES) built.put(alias.item().get().asItem(), Ids.of(alias.namespace(), alias.path()));
            models = built;
            resolved = models;
        }
        var model = models.get(item);
        return model == null ? BuiltInRegistries.ITEM.getKey(item) : model;
    }
}
