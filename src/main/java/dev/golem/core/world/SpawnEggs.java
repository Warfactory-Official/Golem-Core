package dev.golem.core.world;

import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;

/**
 * A spawn egg for a mod's own mob, on every node.
 *
 * <p>26.1 names the egg's entity in the item's properties ({@code Item.Properties.spawnEgg}) and colours it from the
 * item model. Before that the egg took the entity type and two colours in its constructor, and on the Forge-family
 * loaders, where items could register before entity types, only through the loader's supplier-taking subclass
 * ({@code ForgeSpawnEggItem}, {@code DeferredSpawnEggItem}). Fabric registers entity types before items (see
 * {@code DeferredRegister.flushOrder}), so the vanilla constructor can resolve the type there.
 *
 * <p>The colours are still passed on every node so the legacy loaders' own colour handlers agree with the item
 * model's tint list, which is what 26.1 draws.
 */
public final class SpawnEggs {

    private SpawnEggs() {}

    /** Creates the egg item; call from an item registration factory, never earlier. */
    public static Item create(Supplier<? extends EntityType<? extends Mob>> type, int primary, int secondary,
                              Item.Properties properties) {
        //? if >=26.1 {
        return new net.minecraft.world.item.SpawnEggItem(properties.spawnEgg(type.get()));
        //?} elif neoforge {
        /*return new net.neoforged.neoforge.common.DeferredSpawnEggItem(type, primary, secondary, properties);
        *///?} elif forge {
        /*return new net.minecraftforge.common.ForgeSpawnEggItem(type, primary, secondary, properties);
        *///?} else {
        /*return new net.minecraft.world.item.SpawnEggItem(type.get(), primary, secondary, properties);
        *///?}
    }

    /** The entity type an egg stack spawns (null when it is not a spawn egg). */
    public static net.minecraft.world.entity.EntityType<?> typeOf(net.minecraft.world.item.ItemStack stack) {
        if (!(stack.getItem() instanceof net.minecraft.world.item.SpawnEggItem egg)) return null;
        //? if >=26.1 {
        return net.minecraft.world.item.SpawnEggItem.getType(stack);
        //?} elif >=1.20.5 {
        /*return egg.getType(stack);
        *///?} else {
        /*return egg.getType(stack.getTag());
        *///?}
    }
}
