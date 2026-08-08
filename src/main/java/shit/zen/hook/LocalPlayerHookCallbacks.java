package shit.zen.hook;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import shit.zen.ZenClient;
import shit.zen.event.impl.GameTickEvent;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.SlowdownEvent;
import shit.zen.event.impl.SprintDecisionEvent;
import shit.zen.event.impl.SprintEvent;
import shit.zen.utils.game.DirectionalInput;

/** Shared LocalPlayer movement/event semantics for Patchify and Mixin. */
public final class LocalPlayerHookCallbacks {
    private static MotionEvent currentMotionEvent;

    private LocalPlayerHookCallbacks() {
    }

    public static MotionEvent onMotion(
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        boolean onGround,
        boolean isPost
    ) {
        if (currentMotionEvent == null) {
            currentMotionEvent = new MotionEvent(isPost, x, y, z, yaw, pitch, onGround);
        } else if (currentMotionEvent.isPost() && isPost) {
            currentMotionEvent.setPre(true);
        }
        MotionEvent event = currentMotionEvent;
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(currentMotionEvent);
            if (isPost) currentMotionEvent = null;
        }
        return event;
    }

    public static SlowdownEvent onSlowDown(boolean slow) {
        if (ZenClient.isReady()) {
            return (SlowdownEvent) ZenClient.instance.getEventBus().call(new SlowdownEvent(slow));
        }
        return new SlowdownEvent(slow);
    }

    public static void onTickSprintEvent(LocalPlayer player) {
        if (player != null && ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(new SprintEvent());
        }
    }

    public static void onAiStep(LocalPlayer player) {
        if (player != null && ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(new GameTickEvent());
        }
    }

    public static boolean applySprintDecision(
        Input input,
        boolean sprinting,
        SprintDecisionEvent.Source source
    ) {
        if (!ZenClient.isReady()) return sprinting;
        SprintDecisionEvent decisionEvent = new SprintDecisionEvent(
            input == null
                ? DirectionalInput.NONE
                : DirectionalInput.fromImpulses(input.forwardImpulse, input.leftImpulse),
            sprinting,
            source
        );
        ZenClient.getInstance().getEventBus().call(decisionEvent);
        return decisionEvent.isSprinting();
    }

    public static double motionX(double fallback) {
        return currentMotionEvent == null ? fallback : currentMotionEvent.x;
    }

    public static double motionY(double fallback) {
        return currentMotionEvent == null ? fallback : currentMotionEvent.y;
    }

    public static double motionZ(double fallback) {
        return currentMotionEvent == null ? fallback : currentMotionEvent.z;
    }

    public static float motionYaw(float fallback) {
        return currentMotionEvent == null ? fallback : currentMotionEvent.yaw;
    }

    public static float motionPitch(float fallback) {
        return currentMotionEvent == null ? fallback : currentMotionEvent.pitch;
    }

    public static boolean motionOnGround(boolean fallback) {
        return currentMotionEvent == null ? fallback : currentMotionEvent.onGround;
    }
}
