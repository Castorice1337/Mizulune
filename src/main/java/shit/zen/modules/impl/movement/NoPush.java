package shit.zen.modules.impl.movement;

import shit.zen.event.impl.SneakEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.event.EventTarget;

public class NoPush
extends Module {
    public NoPush() {
        super("NoPush", Category.MOVEMENT);
    }

    @EventTarget
    public void onSneak(SneakEvent sneakEvent) {
        if (!FireballBlink.INSTANCE.isEnabled()) {
            sneakEvent.setCancelled(true);
        }
    }
}