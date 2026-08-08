package shit.zen.fabric.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.InputHookCallbacks;

/** Mouse button and look-delta adapter for 26.2. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void mizulune$onPress(
            long window,
            MouseButtonInfo button,
            int action,
            CallbackInfo callbackInfo) {
        InputHookCallbacks.onMousePress(
            (MouseHandler) (Object) this, button.button(), action);
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void mizulune$turnPlayerHead(double frameTime, CallbackInfo callbackInfo) {
        InputHookCallbacks.onTurnPlayerHead();
    }

    @Inject(method = "turnPlayer", at = @At("TAIL"))
    private void mizulune$turnPlayerTail(double frameTime, CallbackInfo callbackInfo) {
        InputHookCallbacks.onTurnPlayerTail();
    }
}
