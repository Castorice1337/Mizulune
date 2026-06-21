package shit.zen.music.config;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.ZenClient;
import shit.zen.manager.ConfigManager;

public final class MusicConfigStore {
    private static final Logger LOGGER = LogManager.getLogger(MusicConfigStore.class);
    private static volatile MusicConfig config = MusicConfig.defaults();

    private MusicConfigStore() {
    }

    public static MusicConfig get() {
        return config;
    }

    public static void read(JsonObject root) {
        try {
            config = MusicConfig.fromJson(root != null && root.has("music") && root.get("music").isJsonObject()
                    ? root.getAsJsonObject("music")
                    : null);
            if (ZenClient.getInstance() != null && ZenClient.getInstance().getMusicService() != null) {
                ZenClient.getInstance().getMusicService().reloadFromConfig(config);
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to read music config; using defaults", exception);
            config = MusicConfig.defaults();
        }
    }

    public static void write(JsonObject root) {
        if (root != null) {
            root.add("music", config.toJson());
        }
    }

    public static void update(MusicConfig nextConfig, boolean save) {
        if (nextConfig == null) {
            return;
        }
        config = nextConfig;
        if (ZenClient.getInstance() != null && ZenClient.getInstance().getMusicService() != null) {
            ZenClient.getInstance().getMusicService().reloadFromConfig(nextConfig);
        }
        if (save) {
            ConfigManager.requestSaveIfReady();
        }
    }
}
