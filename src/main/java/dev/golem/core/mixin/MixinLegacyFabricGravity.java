package dev.golem.core.mixin;

//? if fabric && <1.20.5 {
/*import dev.golem.core.world.Physics;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/^*
 * Fabric 1.20.1: living entities fall with the backported gravity attribute ({@link Physics#gravity}), as 1.20.5 and
 * Forge 1.20.1 (its {@code ENTITY_GRAVITY}) do.
 *
 * <p>Why a mixin (§7). Rung 1: vanilla 1.20.1 writes gravity as the constant 0.08 in {@code travel}. Rung 2: Fabric has no
 * gravity hook (Forge has its own attribute, which is why this is Fabric-only). Rung 3: not a visibility problem. The
 * smallest mixin replaces that one constant with the attribute's value (0.08 unless modified).
 ^/
@Mixin(LivingEntity.class)
public abstract class MixinLegacyFabricGravity {

    @ModifyConstant(method = "travel", constant = @Constant(doubleValue = 0.08D))
    private double golem$gravity(double original) {
        var entity = (LivingEntity) (Object) this;
        return Physics.effectiveGravity(entity, Physics.gravity(entity).getValue());
    }
}
*///?}
