package shit.zen.fabric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shit.zen.hook.BlockHookCallbacks;
import shit.zen.hook.HookDecision;

/** Fabric adapter for XRay chunk face culling. */
@Mixin(Block.class)
abstract class BlockMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void mizulune$shouldRenderFace(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            Direction direction,
            BlockPos neighborPos,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        HookDecision<Boolean> decision = BlockHookCallbacks.shouldRenderFace(state);
        if (decision.handled()) {
            callbackInfo.setReturnValue(decision.value());
        }
    }
}
