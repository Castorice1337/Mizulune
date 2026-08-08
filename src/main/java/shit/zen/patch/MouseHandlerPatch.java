package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.MouseHandler;
import shit.zen.hook.InputHookCallbacks;

@Patch(MouseHandler.class)
public class MouseHandlerPatch {
    @Inject(method = "onPress", desc = "(JIII)V", at = @At(At.Type.HEAD))
    public static void onPress(MouseHandler handler, long window, int button, int action, int modifiers,
                               CallbackInfo callbackInfo) {
        InputHookCallbacks.onMousePress(handler, button, action);
    }

    @Inject(method = "turnPlayer", desc = "()V", at = @At(At.Type.HEAD))
    public static void onTurnPlayerHead(MouseHandler handler, CallbackInfo callbackInfo) {
        InputHookCallbacks.onTurnPlayerHead();
    }

    @Inject(method = "turnPlayer", desc = "()V", at = @At(At.Type.TAIL))
    public static void onTurnPlayerTail(MouseHandler handler, CallbackInfo callbackInfo) {
        InputHookCallbacks.onTurnPlayerTail();
    }
}
