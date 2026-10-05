package dev.golem.core.mixin;

//? if fabric && <1.21 {

/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.golem.core.world.CompatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/^*
 * Fabric 1.20.1 only: gives {@link CompatBlock} the pre-removal explosion hook every other node has.
 * Vanilla's block loop sets the cell to air and only then calls {@code wasExploded}, so a block that
 * has to read its own state (a primed explosive, a multiblock cell) is asked here, just before that
 * {@code setBlock}; a hook that answers true owns the removal and the vanilla call is skipped. A MixinExtras
 * {@code @WrapOperation}, so it chains with other mods hooking the same call (a {@code @Redirect} admits only one).
 ^/
@Mixin(Explosion.class)
public abstract class MixinExplosionBlockHook {

    @WrapOperation(method = "finalizeExplosion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean golem$explodeBlock(Level level, BlockPos pos, BlockState air, int flags,
            Operation<Boolean> original) {
        BlockState state = level.getBlockState(pos);
        if (level instanceof ServerLevel server && state.getBlock() instanceof CompatBlock block
                && block.golem$explosionHit(state, server, pos, (Explosion) (Object) this)) {
            return true;
        }
        return original.call(level, pos, air, flags);
    }
}
*///?}
