package shit.zen.modules.impl.movement;

import java.util.HashMap;
import net.minecraft.client.KeyMapping;
import shit.zen.event.impl.RotationEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.combat.WTap;
import shit.zen.modules.impl.player.InventoryManager;
import shit.zen.event.EventTarget;

public class Sprint
        extends Module {
    private final HashMap<String, String> keyMappings = new HashMap<>();

    public Sprint() {
        super("Sprint", Category.MOVEMENT);
        this.setEnabled(true);
    }

    @Override
    public String getSuffix() {
        return "Legit";
    }

    @EventTarget
    public void onRotation(RotationEvent rotationEvent) {
        if (GuiMove.INSTANCE.isEnabled() && InventoryManager.isPerformingAction) {
            return;
        }

        mc.options.toggleSprint().set(false);

        if (WTap.INSTANCE != null && WTap.INSTANCE.shouldSuppressSprint()) {
            KeyMapping.set(mc.options.keySprint.getKey(), false);
            if (mc.player != null) {
                mc.player.setSprinting(false);
            }
            return;
        }

        KeyMapping.set(mc.options.keySprint.getKey(), true);
    }
}
