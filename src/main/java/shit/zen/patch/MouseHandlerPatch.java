package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.MouseHandler;
import net.minecraft.util.Mth;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.utils.rotation.RotationHandler;

@Patch(MouseHandler.class)
public class MouseHandlerPatch {
    private static float beforeYaw;
    private static float beforePitch;
    private static boolean capturedRotation;

    @Inject(method = "turnPlayer", desc = "()V", at = @At(At.Type.HEAD))
    public static void onTurnPlayerHead(MouseHandler handler, CallbackInfo callbackInfo) {
        capturedRotation = false;
        if (!ZenClient.isReady() || ClientBase.mc.player == null) {
            return;
        }
        beforeYaw = ClientBase.mc.player.getYRot();
        beforePitch = ClientBase.mc.player.getXRot();
        capturedRotation = true;
    }

    @Inject(method = "turnPlayer", desc = "()V", at = @At(At.Type.TAIL))
    public static void onTurnPlayerTail(MouseHandler handler, CallbackInfo callbackInfo) {
        if (!capturedRotation || !ZenClient.isReady() || ClientBase.mc.player == null) {
            capturedRotation = false;
            return;
        }
        float yawDelta = Mth.wrapDegrees(ClientBase.mc.player.getYRot() - beforeYaw);
        float pitchDelta = ClientBase.mc.player.getXRot() - beforePitch;
        capturedRotation = false;
        RotationHandler.offsetChangeLookRotation(yawDelta, pitchDelta);
    }
}
