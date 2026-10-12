package dev.golem.core.mixin;

// CompatBlock.onSustainPlant (1.7 Block.canSustainPlant) on Fabric. Rung 1: vanilla asks the plant, never the soil
// (VegetationBlock/BushBlock.mayPlaceOn reads a block tag or a hard-coded block, so a soil block cannot answer per
// position, e.g. "only the cells next to my core"). Rung 2: NeoForge and Forge ask the soil through
// IBlockExtension/IForgeBlock.canSustainPlant, which CompatBlock overrides; fabric-api has no soil event. Rung 3: an
// access widener cannot add a callback. So one cancellable HEAD inject into the plant's canSurvive, answering only when
// the block below is a CompatBlock that does not PASS; everything else (vanilla soils, other mods) is untouched. Crops
// keep their own light check, which runs before this super call, as on Forge.
//? if fabric {

/*import dev.golem.core.hook.Verdict;
import dev.golem.core.world.CompatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if >=26.1 {
@Mixin(net.minecraft.world.level.block.VegetationBlock.class)
//?} else {
/^@Mixin(net.minecraft.world.level.block.BushBlock.class)
^///?}
public class MixinVegetationSoil {

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void golem$soil(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockPos below = pos.below();
        BlockState soil = level.getBlockState(below);
        if (soil.getBlock() instanceof CompatBlock block) {
            Verdict verdict = block.sustainsPlant(level, below, soil, state);
            if (verdict != Verdict.PASS) cir.setReturnValue(verdict == Verdict.ALLOW);
        }
    }
}
*///?}
