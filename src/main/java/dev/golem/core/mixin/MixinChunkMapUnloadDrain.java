package dev.golem.core.mixin;

//? if >=1.21.1 && <26.1 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Queue;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * 1.21.1 hangs on world exit: a chunk whose generation task still waits for its FULL step on the server thread requeues
 * its unload at once, and {@code stopServer} drains the queue without a time limit, so the server thread never runs
 * that step. Each call runs only the unloads queued when it started; a requeued one waits for the next call.
 *
 * <p>Why a mixin (AGENTS.md section 7): the drain loop is private to {@code ChunkMap.processUnloads}, no loader event
 * runs inside it, and widening it changes nothing. One HEAD {@code @Inject} and one {@code @WrapOperation} on its poll.
 ^/
@Mixin(ChunkMap.class)
public abstract class MixinChunkMapUnloadDrain {

    @Shadow @Final private Queue<Runnable> unloadQueue;

    @Unique private int golem$unloadBudget = -1;

    @Inject(method = "processUnloads", at = @At("HEAD"))
    private void golem$resetBudget(CallbackInfo ci) {
        golem$unloadBudget = -1;
    }

    @WrapOperation(method = "processUnloads", at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;"))
    private Object golem$boundedPoll(Queue<Runnable> queue, Operation<Object> original) {
        if (golem$unloadBudget < 0) golem$unloadBudget = unloadQueue.size();
        if (golem$unloadBudget == 0) return null;
        golem$unloadBudget--;
        return original.call(queue);
    }
}
*///?}
