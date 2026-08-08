package shit.zen.patch;

import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.KeyboardHandler;
import shit.zen.hook.HookDecision;
import shit.zen.hook.InputHookCallbacks;

@Patch(KeyboardHandler.class)
public class KeyboardHandlerPatch {
    @Inject(method = "keyPress", desc = "(JIIII)V")
    public static void onKeyPress(KeyboardHandler handler, long window, int keyCode, int scanCode, int action, int modifiers, CallbackInfo callbackInfo) {
        HookDecision<Void> decision = InputHookCallbacks.onKeyPress(handler, keyCode, action);
        if (decision.handled()) callbackInfo.cancel();
    }
}
