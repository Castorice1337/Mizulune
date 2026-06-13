package shit.zen.modules.impl.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.ModeSetting;
import shit.zen.settings.impl.NumberSetting;

public class Notification extends Module {
    public static Notification INSTANCE;
    private static final int MAX_VISIBLE = 3;

    public final ModeSetting modeSetting = new ModeSetting("Mode", "Dynamic Island").withDefault("Dynamic Island");
    public final NumberSetting durationSetting = new NumberSetting("Duration", 2200, 800, 5000, 100);
    private final List<IslandNotification> queue = new ArrayList<>();

    public Notification() {
        super("Notification", Category.RENDER);
        INSTANCE = this;
    }

    public static void submitModuleToggle(Module module, boolean enabled) {
        if (module == null || INSTANCE == null || !INSTANCE.canSubmitToIsland()) {
            return;
        }
        INSTANCE.enqueue(module.getName(), enabled);
    }

    public static List<IslandNotification> visibleNotifications(long now) {
        if (INSTANCE == null || !INSTANCE.canSubmitToIsland()) {
            return List.of();
        }
        return INSTANCE.snapshotVisible(now);
    }

    private boolean canSubmitToIsland() {
        return this.isEnabled()
                && this.modeSetting.is("Dynamic Island")
                && DynamicIsland.INSTANCE != null
                && DynamicIsland.INSTANCE.isEnabled()
                && DynamicIsland.INSTANCE.isLiquidGlassMode();
    }

    private synchronized void enqueue(String moduleName, boolean enabled) {
        this.queue.add(new IslandNotification(moduleName, enabled, this.durationSetting.getValue().longValue()));
        while (this.queue.size() > 16) {
            this.queue.remove(0);
        }
    }

    private synchronized List<IslandNotification> snapshotVisible(long now) {
        int visible = 0;
        Iterator<IslandNotification> iterator = this.queue.iterator();
        while (iterator.hasNext()) {
            IslandNotification notification = iterator.next();
            if (notification.startedAt > 0L && notification.isExpired(now)) {
                iterator.remove();
                continue;
            }
            if (visible < MAX_VISIBLE) {
                notification.start(now);
                visible++;
            }
        }
        List<IslandNotification> result = new ArrayList<>(Math.min(visible, MAX_VISIBLE));
        for (IslandNotification notification : this.queue) {
            if (notification.startedAt <= 0L) {
                continue;
            }
            result.add(notification.copy());
            if (result.size() >= MAX_VISIBLE) {
                break;
            }
        }
        return result;
    }

    @Override
    protected void onDisable() {
        synchronized (this) {
            this.queue.clear();
        }
    }

    public static final class IslandNotification {
        private final String moduleName;
        private final boolean enabled;
        private final long durationMs;
        private long startedAt;

        private IslandNotification(String moduleName, boolean enabled, long durationMs) {
            this.moduleName = moduleName;
            this.enabled = enabled;
            this.durationMs = Math.max(1L, durationMs);
            this.startedAt = -1L;
        }

        private IslandNotification(String moduleName, boolean enabled, long durationMs, long startedAt) {
            this.moduleName = moduleName;
            this.enabled = enabled;
            this.durationMs = Math.max(1L, durationMs);
            this.startedAt = startedAt;
        }

        private void start(long now) {
            if (this.startedAt <= 0L) {
                this.startedAt = now;
            }
        }

        private boolean isExpired(long now) {
            return now - this.startedAt >= this.durationMs;
        }

        private IslandNotification copy() {
            return new IslandNotification(this.moduleName, this.enabled, this.durationMs, this.startedAt);
        }

        public String getModuleName() {
            return this.moduleName;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public long getStartedAt() {
            return this.startedAt;
        }

        public float getVisibleProgress(long now) {
            if (this.startedAt <= 0L) {
                return 0.0f;
            }
            float age = (float)(now - this.startedAt);
            float fadeIn = Math.min(1.0f, age / 180.0f);
            float fadeOut = Math.min(1.0f, Math.max(0.0f, (this.durationMs - age) / 220.0f));
            return Math.max(0.0f, Math.min(fadeIn, fadeOut));
        }
    }
}
