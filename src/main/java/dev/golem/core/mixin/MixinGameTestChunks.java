package dev.golem.core.mixin;

//? if >=26.1 {
import dev.golem.core.gametest.TestChunks;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRunner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link TestChunks}: a 26.1 GameTest declared through {@code GameTests} does not tick until every chunk of its plot
 * (structure box plus padding, at least 16 blocks) is entity-ticking, not only the chunk of its 1x1x1 structure.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla's only start gate is the private structure-chunk check inside
 * {@code GameTestInfo.tick}, which reads the structure's own bounding box. The public listeners
 * ({@code GameTestListener}, {@code GameTestBatchListener}) cannot hold a test back, test environments see neither
 * tests nor positions, and a larger template would move every test's origin and barriers. Waiting inside the test
 * body shifts the test's tick count and runs inside a sequence event, where it may not start a sequence.
 * (2) NeoForge 26.1 has only {@code RegisterGameTestsEvent} and Fabric's GameTest API only a test-method invoker,
 * which runs on tick 0, after the start. (3) Widening the private field would still need a call at that point of
 * the tick. One cancellable HEAD {@code @Inject} on {@code tick} cancels only before the test has started, only for
 * a Golem test, and only while its chunks are not entity-ticking, as vanilla's check does for the structure chunk.
 * Listed on the 26.1 nodes only ({@code gametest_mixins}); below 26.1 the padded templates are the whole plot.
 */
@Mixin(GameTestInfo.class)
public abstract class MixinGameTestChunks {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void golem$holdUntilPlotTicks(GameTestRunner runner, CallbackInfo ci) {
        if (TestChunks.holdStart((GameTestInfo) (Object) this)) ci.cancel();
    }
}
//?}
