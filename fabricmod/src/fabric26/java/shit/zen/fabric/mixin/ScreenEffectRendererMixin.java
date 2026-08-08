package shit.zen.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import shit.zen.hook.RenderHookCallbacks;

/** Fabric 26.2 adapter for first-person block and fire overlay submission. */
@Mixin(ScreenEffectRenderer.class)
abstract class ScreenEffectRendererMixin {
    @ModifyExpressionValue(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;getViewBlockingState(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState mizulune$filterBlockOverlay(BlockState state) {
        return RenderHookCallbacks.onBlockOverlay().handled() ? null : state;
    }

    @ModifyExpressionValue(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;isOnFire()Z"))
    private boolean mizulune$filterFireOverlay(boolean onFire) {
        return onFire && !RenderHookCallbacks.onFireOverlay().handled();
    }
}
