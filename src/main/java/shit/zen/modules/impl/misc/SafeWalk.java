package shit.zen.modules.impl.misc;

import com.mojang.blaze3d.platform.InputConstants;
import shit.zen.event.impl.MotionEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.event.EventTarget;

public class SafeWalk
extends Module {
    public SafeWalk() {
        super("SafeWalk", Category.MISC);
    }

    public static boolean isOnBlockEdge(float inset) {
        return !mc.level.getCollisions(mc.player, mc.player.getBoundingBox().move(0.0, -0.5, 0.0).inflate(-inset, 0.0, -inset)).iterator().hasNext();
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        if (motionEvent.isPost() && mc.player != null && mc.level != null) {
            mc.options.keyShift.setDown(mc.player.onGround() && SafeWalk.isOnBlockEdge(0.3f));
        }
    }

    @Override
    public void onDisable() {
        boolean keyDown = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyShift.getKey().getValue());
        mc.options.keyShift.setDown(keyDown);
    }
}