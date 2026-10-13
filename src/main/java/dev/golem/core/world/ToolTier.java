package dev.golem.core.world;

import java.util.Objects;
import java.util.function.Supplier;
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
 * <p>A value class with the accessors of 26.1's record. The repair tag may be given lazily ({@link #of}): a
 * {@code TagKey} names the item registry, which on 1.20.1 cannot be done before bootstrap, and tool materials
 * are typically constants of a registration class that must also initialise in a plain JVM.
 */
public final class ToolTier {

    private final int level;
    private final int uses;
    private final float speed;
    private final float attackDamageBonus;
    private final int enchantability;
    private final Supplier<@Nullable TagKey<Item>> repairItems;
    private volatile boolean repairResolved;
    private @Nullable TagKey<Item> repairTag;

    /**
     * @param level             legacy harvest level: 0 wood/gold, 1 stone, 2 iron, 3 diamond, 4+ netherite
     * @param uses              durability; {@code 0} means the tool never wears (the legacy meaning)
     * @param speed             mining speed on blocks the tool is effective against
     * @param attackDamageBonus the material's attack bonus (used only where a vanilla base class adds it)
     * @param enchantability    enchanting-table value; {@code 0} means the table offers nothing
     * @param repairItems       items that repair the tool on an anvil, or {@code null} for none
     */
    public ToolTier(int level, int uses, float speed, float attackDamageBonus, int enchantability,
                    @Nullable TagKey<Item> repairItems) {
        this(level, uses, speed, attackDamageBonus, enchantability, () -> repairItems, true);
    }

    private ToolTier(int level, int uses, float speed, float attackDamageBonus, int enchantability,
                     Supplier<@Nullable TagKey<Item>> repairItems, boolean unused) {
        this.level = level;
        this.uses = uses;
        this.speed = speed;
        this.attackDamageBonus = attackDamageBonus;
        this.enchantability = enchantability;
        this.repairItems = Objects.requireNonNull(repairItems);
    }

    /**
     * As the constructor, with the repair tag named on first use:
     * {@code ToolTier.of(3, 750, 8F, 2F, 10, () -> TagKey.create(Registries.ITEM, id))}. The supplier may
     * return {@code null} for "no repair item".
     */
    public static ToolTier of(int level, int uses, float speed, float attackDamageBonus, int enchantability,
                              Supplier<@Nullable TagKey<Item>> repairItems) {
        return new ToolTier(level, uses, speed, attackDamageBonus, enchantability, repairItems, true);
    }

    public int level() {
        return level;
    }

    public int uses() {
        return uses;
    }

    public float speed() {
        return speed;
    }

    public float attackDamageBonus() {
        return attackDamageBonus;
    }

    public int enchantability() {
        return enchantability;
    }

    /** Items that repair the tool on an anvil, or {@code null} for none. */
    public @Nullable TagKey<Item> repairItems() {
        if (!repairResolved) {
            synchronized (this) {
                if (!repairResolved) {
                    repairTag = repairItems.get();
                    repairResolved = true;
                }
            }
        }
        return repairTag;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof ToolTier other
                && level == other.level && uses == other.uses
                && Float.compare(speed, other.speed) == 0
                && Float.compare(attackDamageBonus, other.attackDamageBonus) == 0
                && enchantability == other.enchantability
                && Objects.equals(repairItems(), other.repairItems());
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, uses, speed, attackDamageBonus, enchantability, repairItems());
    }

    @Override
    public String toString() {
        return "ToolTier[level=" + level + ", uses=" + uses + ", speed=" + speed + ", attackDamageBonus="
                + attackDamageBonus + ", enchantability=" + enchantability + ", repairItems=" + repairItems() + "]";
    }
}
