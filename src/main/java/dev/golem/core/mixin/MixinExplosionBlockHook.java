package dev.golem.core.mixin;

//? if fabric && <1.21 {

/*import dev.golem.core.world.CompatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/^*
 * Fabric 1.20.1 only: gives {@link CompatBlock} the pre-removal explosion hook every other node has.
 * Vanilla's block loop sets the cell to air and only then calls {@code wasExploded}, so a block that
 * has to read its own state (a primed explosive, a multiblock cell) is asked here, just before that
 * {@code setBlock}; a hook that answers true owns the removal and the vanilla call is skipped.
 ^/
@Mixin(Explosion.class)
public abstract class MixinExplosionBlockHook {

    @Redirect(method = "finalizeExplosion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean golem$explodeBlock(Level level, BlockPos pos, BlockState air, int flags) {
        BlockState state = level.getBlockState(pos);
        if (level instanceof ServerLevel server && state.getBlock() instanceof CompatBlock block
                && block.golem$explosionHit(state, server, pos, (Explosion) (Object) this)) {
            return true;
        }
        return level.setBlock(pos, air, flags);
    }
}
*///?}
