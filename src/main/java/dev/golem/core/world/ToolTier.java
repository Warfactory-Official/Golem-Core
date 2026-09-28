package dev.golem.core.world;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * A tool material in the shape every supported version can be built from.
 *
 * <p>26.1 describes a material as the {@code ToolMaterial} record keyed by an "incorrect for drops"
 * block tag; 1.21.1 as the {@code Tier} interface with the same tag; 1.20.1 as {@code Tier} with an
 * integer harvest level. The integer level is the one thing all three can be derived from, and it is
 * what legacy content was written against, so that is what this carries.
 *
 * @param level            legacy harvest level: 0 wood/gold, 1 stone, 2 iron, 3 diamond, 4+ netherite
 * @param uses             durability; {@code 0} means the tool never wears (the legacy meaning)
 * @param speed            mining speed on blocks the tool is effective against
 * @param attackDamageBonus the material's attack bonus (used only where a vanilla base class adds it)
 * @param enchantability   enchanting-table value; {@code 0} means the table offers nothing
 * @param repairItems      items that repair the tool on an anvil, or {@code null} for none
 */
public record ToolTier(int level, int uses, float speed, float attackDamageBonus, int enchantability,
                       @Nullable TagKey<Item> repairItems) {
}
