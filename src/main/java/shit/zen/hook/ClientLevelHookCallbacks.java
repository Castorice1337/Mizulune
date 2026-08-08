package shit.zen.hook;

import net.minecraft.world.entity.Entity;
import shit.zen.ClientBase;

/** Shared client-level delayed packet gate for Patchify and Mixin. */
public final class ClientLevelHookCallbacks {
    private ClientLevelHookCallbacks() {
    }

    public static boolean consumeDelayedPlayerTask(Entity entity) {
        if (ClientBase.mc == null
                || entity != ClientBase.mc.player
                || ClientBase.delayPackets.isEmpty()) {
            return false;
        }
        Runnable delayed = ClientBase.delayPackets.poll();
        if (delayed != null) {
            delayed.run();
        }
        return true;
    }
}
