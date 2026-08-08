package shit.zen.hook;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.EntityRemoveEvent;

/** Shared local-player yaw and attack lifecycle semantics for Patchify and Mixin. */
public final class PlayerHookCallbacks {
    private PlayerHookCallbacks() {
    }

    public static float actionYaw(Player player, float originalYaw) {
        return isLocalPlayer(player) ? ClientBase.yaw : originalYaw;
    }

    public static void onAttack(Player player, Entity target, boolean post) {
        if (isLocalPlayer(player)) {
            ZenClient.getInstance().getEventBus().call(new EntityRemoveEvent(post, target));
        }
    }

    private static boolean isLocalPlayer(Player player) {
        return player != null
                && ZenClient.isReady()
                && ClientBase.mc != null
                && player == ClientBase.mc.player;
    }
}
