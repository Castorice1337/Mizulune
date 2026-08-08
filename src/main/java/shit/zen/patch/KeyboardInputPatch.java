package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.hook.KeyboardInputHookCallbacks;

@Patch(KeyboardInput.class)
public class KeyboardInputPatch extends Input {
    @Inject(method = "tick", desc = "(ZF)V", at = @At(At.Type.TAIL))
    public static void onTick(
            KeyboardInput input,
            boolean slowDown,
            float sneakMultiplier,
            CallbackInfo callbackInfo) {
        KeyboardInputHookCallbacks.onTick(input, slowDown, sneakMultiplier);
    }

    static AppliedInput applyEvent(StrafeEvent event, boolean slowDown, float sneakMultiplier) {
        KeyboardInputHookCallbacks.AppliedInput applied =
                KeyboardInputHookCallbacks.applyEvent(event, slowDown, sneakMultiplier);
        return new AppliedInput(
                applied.forward(),
                applied.strafe(),
                applied.jumping(),
                applied.sneaking());
    }

    record AppliedInput(float forward, float strafe, boolean jumping, boolean sneaking) {
    }
}
