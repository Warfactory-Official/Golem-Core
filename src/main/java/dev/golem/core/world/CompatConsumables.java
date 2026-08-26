package dev.golem.core.world;

import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;

/** Builds edible item properties across the component and pre-component food APIs. */
public final class CompatConsumables {

    private CompatConsumables() {}

    public static Item.Properties food(Item.Properties properties, int nutrition, float saturation,
                                        List<MobEffectInstance> effects) {
        return food(properties, nutrition, saturation, effects, false);
    }

    /** Builds food that can be consumed without a full hunger bar. */
    public static Item.Properties foodAlwaysEdible(Item.Properties properties, int nutrition, float saturation,
                                                    List<MobEffectInstance> effects) {
        return food(properties, nutrition, saturation, effects, true);
    }

    private static Item.Properties food(Item.Properties properties, int nutrition, float saturation,
                                        List<MobEffectInstance> effects, boolean alwaysEdible) {
        //? if >=1.21.2 {
        net.minecraft.world.item.component.Consumable.Builder consumable =
                net.minecraft.world.item.component.Consumables.defaultFood();
        if (!effects.isEmpty()) {
            consumable.onConsume(new net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect(effects));
        }
        return properties.food(ItemStacks.food(nutrition, saturation, alwaysEdible), consumable.build());
        //?} else if >=1.20.5 {
        /*net.minecraft.world.food.FoodProperties.Builder food =
                new net.minecraft.world.food.FoodProperties.Builder().nutrition(nutrition);
        food.saturationModifier(saturation);
        //? if >=1.21.1 {
        if (alwaysEdible) food.alwaysEdible();
        //?} else {
        /^if (alwaysEdible) food.alwaysEat();
        ^///?}
        effects.forEach(effect -> food.effect(effect, 1.0F));
        return properties.food(food.build());
        *///?} else {
        /*net.minecraft.world.food.FoodProperties.Builder food =
                new net.minecraft.world.food.FoodProperties.Builder().nutrition(nutrition)
                        .saturationMod(saturation);
        if (alwaysEdible) food.alwaysEat();
        effects.forEach(effect -> food.effect(effect, 1.0F));
        return properties.food(food.build());
        *///?}
    }

    /** The 26.1 use-time remainder path; older targets use the item's finish hook instead. */
    public static Item.Properties usingConvertsTo(Item.Properties properties, Item remainder) {
        //? if >=26.1 {
        return properties.usingConvertsTo(remainder);
        //?} else {
        /*return properties;
        *///?}
    }
}
