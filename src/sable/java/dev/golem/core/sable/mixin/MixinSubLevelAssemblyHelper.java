package dev.golem.core.sable.mixin;

import dev.golem.core.sable.SableMoves;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets Sable's one block-moving routine so {@link dev.golem.core.world.SubLevels} can say "a move is under way"
 * and tell mods where each block went. {@code moveBlocks} is the funnel for every move Sable makes: assembly
 * ({@code assembleBlocks}), splitting (which assembles each piece), and Create Aeronautics' disassembly back into
 * the world (which calls it directly, with a rotation).
 *
 * <p>Why a mixin (AGENTS.md section 7). Rungs 1-3 were tried:
 * <ol>
 *   <li>Vanilla: a moved block is placed with {@code onPlace} suppressed and removed with {@code isMoving=true},
 *       the same flag a piston uses; nothing says "carried elsewhere" or names the destination.</li>
 *   <li>Sable's API: its per-block hook, {@code BlockSubLevelAssemblyListener}, has to be implemented by the
 *       {@code Block} class itself. Sable is an optional peer on one node, so the shared tree cannot implement
 *       its interface (a block class naming it would fail to load without Sable), and the hook is per block,
 *       while carrying a structure or a network over needs the whole batch's old-to-new map. Sable's split
 *       listener covers splits only, not assembly or disassembly.</li>
 *   <li>Access widening does not apply: nothing is hidden, there is no batch callback.</li>
 * </ol>
 * Two {@code @Inject}s at HEAD and RETURN of a public static API method, and one {@code @ModifyVariable} widening
 * {@code assembleBlocks}' block set (Sable's split listener covers splits only), no redirect or overwrite. The config
 * applies only when Sable is loaded ({@link SableMixinPlugin}).
 */
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class MixinSubLevelAssemblyHelper {

    /** Assembly carries whole structures, as Sable's split listener already does for splits (same rungs as below). */
    @ModifyVariable(method = "assembleBlocks", at = @At("HEAD"), argsOnly = true)
    private static Iterable<BlockPos> golem$wholeStructures(Iterable<BlockPos> blocks, ServerLevel level) {
        return SableMoves.wholeStructures(level, blocks);
    }

    @Inject(method = "moveBlocks", at = @At("HEAD"))
    private static void golem$beginMove(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                        Iterable<BlockPos> blocks, CallbackInfo ci) {
        SableMoves.begin(level, transform, blocks);
    }

    @Inject(method = "moveBlocks", at = @At("RETURN"))
    private static void golem$endMove(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                      Iterable<BlockPos> blocks, CallbackInfo ci) {
        SableMoves.end();
    }
}
