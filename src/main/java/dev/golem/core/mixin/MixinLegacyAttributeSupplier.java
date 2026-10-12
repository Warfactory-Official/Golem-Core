package dev.golem.core.mixin;

//? if <1.20.5 {
/*import dev.golem.core.world.LegacyPhysics;
import java.util.function.Consumer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * 1.20.1: every living entity has the backported physics attributes ({@link LegacyPhysics}), created on first use.
 *
 * <p>Why a mixin (§7). The attributes must exist on every living entity, vanilla and modded. Rung 1, vanilla: an
 * entity's attribute supplier is built by {@code DefaultAttributes} during bootstrap, before any mod can register an
 * attribute, so the supplier cannot list them. Rung 2, loaders: Forge's {@code EntityAttributeModificationEvent} covers
 * Forge only; Fabric has no way to add an attribute to another mod's or vanilla's supplier. Rung 3: visibility is not the
 * obstacle. The smallest mixin answers {@code createInstance} for these three attributes when the supplier does not
 * list them, which is the call {@code AttributeMap.getInstance} makes; the instance is then synced like any other.
 ^/
@Mixin(AttributeSupplier.class)
public abstract class MixinLegacyAttributeSupplier {

    @Inject(method = "createInstance", at = @At("RETURN"), cancellable = true)
    private void golem$physics(Consumer<AttributeInstance> onDirty, Attribute attribute,
                               CallbackInfoReturnable<AttributeInstance> cir) {
        if (cir.getReturnValue() == null && LegacyPhysics.isBackported(attribute)) {
            cir.setReturnValue(new AttributeInstance(attribute, onDirty));
        }
    }
}
*///?}
