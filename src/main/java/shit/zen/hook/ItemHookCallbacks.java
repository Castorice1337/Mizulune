package shit.zen.hook;

import net.minecraft.world.entity.player.Player;
import shit.zen.ZenClient;
import shit.zen.event.impl.UseItemRayTraceEvent;

/** Shared item POV ray-trace rotation semantics for Patchify and Mixin. */
public final class ItemHookCallbacks {
    private ItemHookCallbacks() {
    }

    public static float pitch(Player player) {
        return event(player).getPitch();
    }

    public static float yaw(Player player) {
        return event(player).getYaw();
    }

    private static UseItemRayTraceEvent event(Player player) {
        UseItemRayTraceEvent event = new UseItemRayTraceEvent(player.getYRot(), player.getXRot());
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(event);
        }
        return event;
    }
}
