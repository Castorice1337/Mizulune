package shit.zen.fabric.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.InputHookCallbacks;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void mizulune$onPress(
        long window,
        int button,
        int action,
        int modifiers,
        CallbackInfo callbackInfo
    ) {
        InputHookCallbacks.onMousePress((MouseHandler) (Object) this, button, action);
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void mizulune$turnPlayerHead(CallbackInfo callbackInfo) {
        InputHookCallbacks.onTurnPlayerHead();
    }

    @Inject(method = "turnPlayer", at = @At("TAIL"))
    private void mizulune$turnPlayerTail(CallbackInfo callbackInfo) {
        InputHookCallbacks.onTurnPlayerTail();
    }
}
