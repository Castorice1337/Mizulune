package shit.zen.modules.impl.player.helper;

import lombok.Getter;
import shit.zen.ClientBase;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.PreMotionEvent;
import shit.zen.event.impl.RenderEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.utils.rotation.Rotation;

public abstract class HelperBase
extends ClientBase {
    @Getter
    private final String name;

    public HelperBase(String string) {
        this.name = string;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void onTick(TickEvent tickEvent) {
    }

    public void onMotion(MotionEvent motionEvent) {
    }

    public void onRender(RenderEvent renderEvent) {
    }

    public void onPreMotion(PreMotionEvent preMotionEvent) {
    }

    public boolean isActive() {
        return false;
    }

    public Rotation getTargetRotation() {
        return null;
    }

    }