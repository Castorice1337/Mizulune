package shit.zen.utils.game;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Mth;
import shit.zen.ClientBase;

public final class PlayerTickDelay {
    private static final Map<Runnable, Object> OWNERS = new ConcurrentHashMap<>();

    public static int add(Object owner, int ticks) {
        if (owner == null) {
            return 0;
        }
        int count = Mth.clamp(ticks, 0, 20);
        for (int i = 0; i < count; i++) {
            Runnable delayed = new OwnedDelay(owner);
            OWNERS.put(delayed, owner);
            ClientBase.delayPackets.add(delayed);
        }
        return count;
    }

    public static void release(Object owner) {
        if (owner == null) {
            return;
        }
        for (Map.Entry<Runnable, Object> entry : OWNERS.entrySet()) {
            if (entry.getValue() == owner) {
                ClientBase.delayPackets.remove(entry.getKey());
                OWNERS.remove(entry.getKey(), owner);
            }
        }
    }

    public static int countOwned(Object owner) {
        if (owner == null) {
            return 0;
        }
        int count = 0;
        for (Runnable delayed : ClientBase.delayPackets) {
            if (OWNERS.get(delayed) == owner) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasExternalTasks(Object owner) {
        for (Runnable delayed : ClientBase.delayPackets) {
            Object taskOwner = OWNERS.get(delayed);
            if (taskOwner != owner) {
                return true;
            }
        }
        return false;
    }

    private PlayerTickDelay() {
    }

    private record OwnedDelay(Object owner) implements Runnable {
        @Override
        public void run() {
            OWNERS.remove(this, this.owner);
        }
    }
}
