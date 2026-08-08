package shit.zen.fabric.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.HookDecision;
import shit.zen.hook.InputHookCallbacks;

/** Keyboard event adapter for the structured 26.2 input event. */
@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void mizulune$keyPress(
            long window,
            int action,
            KeyEvent event,
            CallbackInfo callbackInfo) {
        HookDecision<Void> decision = InputHookCallbacks.onKeyPress(
            (KeyboardHandler) (Object) this, event.key(), action);
        if (decision.handled()) callbackInfo.cancel();
    }
}
