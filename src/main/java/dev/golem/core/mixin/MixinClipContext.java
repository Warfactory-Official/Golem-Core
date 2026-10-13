package dev.golem.core.mixin;

import net.minecraft.world.level.ClipContext;
import org.spongepowered.asm.mixin.Mixin;

//? if <1.20.5 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.At;
*///?}

/**
 * Lets a block raytrace be built without an entity below 1.20.5.
 *
 * <p>1.20.5 gave {@code ClipContext} a constructor taking a {@code CollisionContext}, which is what
 * {@code Compat.clip} needs for an entity-less trace. 1.20.1 has only the entity constructor, and it
 * calls {@code CollisionContext.of(entity)} without a null check, so the null that reads as "no
 * entity" there crashes inside {@code EntityCollisionContext}. This routes a null entity to the empty
 * context, which is what the later constructor does. Empty on every other node. A MixinExtras
 * {@code @WrapOperation} rather than a {@code @Redirect}, so another mod's hook on the same call still runs.
 */
@Mixin(ClipContext.class)
public abstract class MixinClipContext {

    //? if <1.20.5 {
    /*@WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/CollisionContext;of(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/shapes/CollisionContext;"))
    private CollisionContext golem$contextOf(@Nullable Entity entity, Operation<CollisionContext> original) {
        return entity == null ? CollisionContext.empty() : original.call(entity);
    }
    *///?}
}
