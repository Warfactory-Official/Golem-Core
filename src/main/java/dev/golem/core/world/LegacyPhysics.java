package dev.golem.core.world;

//? if <1.20.5 {
/*import dev.golem.core.GolemCore;
import dev.golem.core.registry.DeferredRegister;
import dev.golem.core.registry.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/^*
 * 1.20.1's backport of the 1.20.5 physics attributes (see {@link Physics}): {@code golemcore:generic.safe_fall_distance}
 * and {@code golemcore:generic.fall_damage_multiplier} on both loaders, and {@code golemcore:generic.gravity} on Fabric
 * (Forge already has {@code forge:entity_gravity}, applied by its own patch). Same defaults and bounds as 1.20.5.
 * Added to every living entity (created on demand by {@code MixinLegacyAttributeSupplier}) and applied by {@code MixinLegacyLivingPhysics}.
 ^/
public final class LegacyPhysics {

    private static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(() -> Registries.ATTRIBUTE, GolemCore.MOD_ID);

    public static final RegistryObject<Attribute> GRAVITY = GolemCore.loader() == GolemCore.Loader.FABRIC
            ? ATTRIBUTES.register("generic.gravity",
                    () -> new RangedAttribute("attribute.name.generic.gravity", 0.08D, -1.0D, 1.0D).setSyncable(true))
            : null;
    public static final RegistryObject<Attribute> SAFE_FALL_DISTANCE = ATTRIBUTES.register("generic.safe_fall_distance",
            () -> new RangedAttribute("attribute.name.generic.safe_fall_distance", 3.0D, -1024.0D, 1024.0D).setSyncable(true));
    public static final RegistryObject<Attribute> FALL_DAMAGE_MULTIPLIER = ATTRIBUTES.register("generic.fall_damage_multiplier",
            () -> new RangedAttribute("attribute.name.generic.fall_damage_multiplier", 1.0D, 0.0D, 100.0D).setSyncable(true));

    private LegacyPhysics() {}

    /^* Golem Core's entrypoints call this so the register exists before any registry is filled. ^/
    public static void init() {}

    /^* Whether {@code attribute} is one of the backported three, created on demand on every living entity. ^/
    public static boolean isBackported(Attribute attribute) {
        return (GRAVITY != null && attribute == GRAVITY.get()) || attribute == SAFE_FALL_DISTANCE.get()
                || attribute == FALL_DAMAGE_MULTIPLIER.get();
    }

}
*///?}
