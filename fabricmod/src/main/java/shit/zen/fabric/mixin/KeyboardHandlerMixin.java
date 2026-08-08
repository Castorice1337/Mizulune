package shit.zen.fabric.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.HookDecision;
import shit.zen.hook.InputHookCallbacks;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void mizulune$keyPress(
        long window,
        int keyCode,
        int scanCode,
        int action,
        int modifiers,
        CallbackInfo callbackInfo
    ) {
        HookDecision<Void> decision = InputHookCallbacks.onKeyPress(
            (KeyboardHandler) (Object) this,
            keyCode,
            action
        );
        if (decision.handled()) callbackInfo.cancel();
    }
}
