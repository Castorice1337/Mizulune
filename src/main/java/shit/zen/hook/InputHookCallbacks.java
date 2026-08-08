package shit.zen.hook;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.MouseHandler;
import net.minecraft.util.Mth;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.KeyEvent;
import shit.zen.event.impl.MouseButtonEvent;
import shit.zen.utils.rotation.RotationHandler;

/** Shared keyboard and mouse callbacks used by both injection adapters. */
public final class InputHookCallbacks {
    private static float beforeYaw;
    private static float beforePitch;
    private static boolean capturedRotation;

    private InputHookCallbacks() {
    }

    public static HookDecision<Void> onKeyPress(
        KeyboardHandler handler,
        int keyCode,
        int action
    ) {
        if (handler == null || !ZenClient.isReady()) return HookDecision.pass();
        KeyEvent event = new KeyEvent(keyCode, action != 0);
        ZenClient.getInstance().getEventBus().call(event);
        return event.isCancelled() ? HookDecision.cancel() : HookDecision.pass();
    }

    public static void onMousePress(MouseHandler handler, int button, int action) {
        if (handler != null && ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(new MouseButtonEvent(button, action));
        }
    }

    public static void onTurnPlayerHead() {
        capturedRotation = false;
        if (!ZenClient.isReady() || ClientBase.mc.player == null) return;
        beforeYaw = ClientBase.mc.player.getYRot();
        beforePitch = ClientBase.mc.player.getXRot();
        capturedRotation = true;
    }

    public static void onTurnPlayerTail() {
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
