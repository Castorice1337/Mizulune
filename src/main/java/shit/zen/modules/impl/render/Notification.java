package shit.zen.modules.impl.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.value.impl.ModeValue;
import shit.zen.value.impl.NumberValue;

public class Notification extends Module {
    public static Notification INSTANCE;
    private static final int MAX_VISIBLE = 3;

    public final ModeValue ModeValue = new ModeValue("Mode", "Dynamic Island").withDefault("Dynamic Island");
    public final NumberValue durationSetting = new NumberValue("Duration", 2200, 800, 5000, 100);
    private final List<IslandNotification> queue = new ArrayList<>();
    private final Map<String, IslandNotification> persistentStatuses = new LinkedHashMap<>();

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

    public static void showPersistentStatus(String key, String title, String status, int accentColor,
                                            IconType iconType, boolean active) {
        if (INSTANCE == null || key == null || key.isBlank()) {
            return;
        }
        INSTANCE.setPersistentStatus(key, title, status, accentColor, iconType, active);
    }

    public static void clearPersistentStatus(String key) {
        if (INSTANCE == null || key == null || key.isBlank()) {
            return;
        }
        synchronized (INSTANCE) {
            INSTANCE.persistentStatuses.remove(key);
        }
    }

    public static List<IslandNotification> visibleNotifications(long now) {
        if (INSTANCE == null || !INSTANCE.canSubmitToIsland()) {
            return List.of();
        }
        return INSTANCE.snapshotVisible(now);
    }

    private boolean canSubmitToIsland() {
        return this.isEnabled()
                && this.ModeValue.is("Dynamic Island")
                && DynamicIsland.INSTANCE != null
                && DynamicIsland.INSTANCE.isEnabled()
                && DynamicIsland.INSTANCE.isLiquidGlassMode();
    }

    private synchronized void enqueue(String moduleName, boolean enabled) {
        this.queue.add(IslandNotification.moduleToggle(moduleName, enabled, this.durationSetting.getValue().longValue()));
        while (this.queue.size() > 16) {
            this.queue.remove(0);
        }
    }

    private synchronized void setPersistentStatus(String key, String title, String status, int accentColor,
                                                  IconType iconType, boolean active) {
        if (!active) {
            this.persistentStatuses.remove(key);
            return;
        }
        this.persistentStatuses.computeIfAbsent(key, ignored -> IslandNotification.persistent(
                title, status, accentColor, iconType == null ? IconType.WARNING : iconType));
    }

    private synchronized List<IslandNotification> snapshotVisible(long now) {
        List<IslandNotification> result = new ArrayList<>(MAX_VISIBLE);
        for (IslandNotification notification : this.persistentStatuses.values()) {
            notification.start(now);
            result.add(notification.copy());
            if (result.size() >= MAX_VISIBLE) {
                return result;
            }
        }

        int queueSlots = MAX_VISIBLE - result.size();
        int visible = 0;
        Iterator<IslandNotification> iterator = this.queue.iterator();
        while (iterator.hasNext()) {
            IslandNotification notification = iterator.next();
            if (notification.startedAt > 0L && notification.isExpired(now)) {
                iterator.remove();
                continue;
            }
            if (visible < queueSlots) {
                notification.start(now);
                visible++;
            }
        }
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
            this.persistentStatuses.clear();
        }
    }

    public enum IconType {
        SUCCESS,
        ERROR,
        WARNING
    }

    public static final class IslandNotification {
        private final String title;
        private final String statusText;
        private final int accentColor;
        private final IconType iconType;
        private final long durationMs;
        private final boolean persistent;
        private long startedAt;

        private IslandNotification(String title, String statusText, int accentColor, IconType iconType,
                                   long durationMs, boolean persistent) {
            this.title = title;
            this.statusText = statusText;
            this.accentColor = accentColor;
            this.iconType = iconType;
            this.durationMs = Math.max(1L, durationMs);
            this.persistent = persistent;
            this.startedAt = -1L;
        }

        private IslandNotification(String title, String statusText, int accentColor, IconType iconType,
                                   long durationMs, boolean persistent, long startedAt) {
            this.title = title;
            this.statusText = statusText;
            this.accentColor = accentColor;
            this.iconType = iconType;
            this.durationMs = Math.max(1L, durationMs);
            this.persistent = persistent;
            this.startedAt = startedAt;
        }

        private static IslandNotification moduleToggle(String moduleName, boolean enabled, long durationMs) {
            return new IslandNotification(
                    moduleName,
                    enabled ? "Enabled" : "Disabled",
                    enabled ? 0xFF71E19B : 0xFFFF7777,
                    enabled ? IconType.SUCCESS : IconType.ERROR,
                    durationMs,
                    false);
        }

        private static IslandNotification persistent(String title, String statusText, int accentColor, IconType iconType) {
            return new IslandNotification(title, statusText, accentColor, iconType, Long.MAX_VALUE, true);
        }

        private void start(long now) {
            if (this.startedAt <= 0L) {
                this.startedAt = now;
            }
        }

        private boolean isExpired(long now) {
            if (this.persistent) {
                return false;
            }
            return now - this.startedAt >= this.durationMs;
        }

        private IslandNotification copy() {
            return new IslandNotification(this.title, this.statusText, this.accentColor, this.iconType,
                    this.durationMs, this.persistent, this.startedAt);
        }

        public String getModuleName() {
            return this.title;
        }

        public String getStatusText() {
            return this.statusText;
        }

        public int getAccentColor() {
            return this.accentColor;
        }

        public IconType getIconType() {
            return this.iconType;
        }

        public boolean isEnabled() {
            return this.iconType == IconType.SUCCESS;
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
            if (this.persistent) {
                return Math.max(0.0f, fadeIn);
            }
            float fadeOut = Math.min(1.0f, Math.max(0.0f, (this.durationMs - age) / 220.0f));
            return Math.max(0.0f, Math.min(fadeIn, fadeOut));
        }
    }
}
