package shit.zen.music.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import shit.zen.music.model.PlayMode;

public class MusicConfig {
    public static final int CURRENT_DISCLAIMER_VERSION = 1;

    private boolean enabled = true;
    private String apiBaseUrl = "https://music-api.gdstudio.xyz/api.php";
    private String defaultSource = "netease";
    private List<String> enabledSources = new ArrayList<>(List.of("netease", "kuwo", "joox"));
    private int preferredBitrate = 320;
    private int searchPageSize = 20;
    private long requestIntervalMs = 1000L;
    private long searchDebounceMs = 500L;
    private boolean cacheEnabled = true;
    private boolean temporaryCacheEnabled = true;
    private int maxCacheSizeMb = 512;
    private boolean clearTemporaryCacheOnExit = true;
    private PlayMode playMode = PlayMode.ORDER;
    private float volume = 0.8f;
    private boolean disclaimerAccepted = false;
    private int disclaimerVersion = CURRENT_DISCLAIMER_VERSION;
    private long disclaimerAcceptedAt = 0L;

    public static MusicConfig defaults() {
        return new MusicConfig();
    }

    public static MusicConfig fromJson(JsonObject object) {
        MusicConfig config = defaults();
        if (object == null) {
            return config;
        }
        config.enabled = readBoolean(object, "enabled", config.enabled);
        config.apiBaseUrl = readString(object, "apiBaseUrl", config.apiBaseUrl);
        config.defaultSource = readString(object, "defaultSource", config.defaultSource);
        config.enabledSources = readStringList(object, "enabledSources", config.enabledSources);
        config.preferredBitrate = readInt(object, "preferredBitrate", config.preferredBitrate, 64, 999);
        config.searchPageSize = readInt(object, "searchPageSize", config.searchPageSize, 1, 50);
        config.requestIntervalMs = readLong(object, "requestIntervalMs", config.requestIntervalMs, 100L, 30000L);
        config.searchDebounceMs = readLong(object, "searchDebounceMs", config.searchDebounceMs, 100L, 3000L);
        config.cacheEnabled = readBoolean(object, "cacheEnabled", config.cacheEnabled);
        config.temporaryCacheEnabled = readBoolean(object, "temporaryCacheEnabled", config.temporaryCacheEnabled);
        config.maxCacheSizeMb = readInt(object, "maxCacheSizeMb", config.maxCacheSizeMb, 64, 4096);
        config.clearTemporaryCacheOnExit = readBoolean(object, "clearTemporaryCacheOnExit", config.clearTemporaryCacheOnExit);
        config.playMode = readMode(object, "playMode", config.playMode);
        config.volume = readFloat(object, "volume", config.volume, 0.0f, 1.0f);
        config.disclaimerAccepted = readBoolean(object, "disclaimerAccepted", config.disclaimerAccepted);
        config.disclaimerVersion = readInt(object, "disclaimerVersion", config.disclaimerVersion, 1, 999);
        config.disclaimerAcceptedAt = readLong(object, "disclaimerAcceptedAt", config.disclaimerAcceptedAt, 0L, Long.MAX_VALUE);
        if (!config.enabledSources.contains(config.defaultSource)) {
            config.defaultSource = config.enabledSources.isEmpty() ? "netease" : config.enabledSources.get(0);
        }
        return config;
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("enabled", this.enabled);
        object.addProperty("apiBaseUrl", this.apiBaseUrl);
        object.addProperty("defaultSource", this.defaultSource);
        JsonArray sources = new JsonArray();
        for (String source : this.enabledSources) {
            sources.add(source);
        }
        object.add("enabledSources", sources);
        object.addProperty("preferredBitrate", this.preferredBitrate);
        object.addProperty("searchPageSize", this.searchPageSize);
        object.addProperty("requestIntervalMs", this.requestIntervalMs);
        object.addProperty("searchDebounceMs", this.searchDebounceMs);
        object.addProperty("cacheEnabled", this.cacheEnabled);
        object.addProperty("temporaryCacheEnabled", this.temporaryCacheEnabled);
        object.addProperty("maxCacheSizeMb", this.maxCacheSizeMb);
        object.addProperty("clearTemporaryCacheOnExit", this.clearTemporaryCacheOnExit);
        object.addProperty("playMode", this.playMode.name());
        object.addProperty("volume", this.volume);
        object.addProperty("disclaimerAccepted", this.disclaimerAccepted);
        object.addProperty("disclaimerVersion", this.disclaimerVersion);
        object.addProperty("disclaimerAcceptedAt", this.disclaimerAcceptedAt);
        return object;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public String getApiBaseUrl() {
        return this.apiBaseUrl;
    }

    public String getDefaultSource() {
        return this.defaultSource;
    }

    public List<String> getEnabledSources() {
        return List.copyOf(this.enabledSources);
    }

    public int getPreferredBitrate() {
        return this.preferredBitrate;
    }

    public int getSearchPageSize() {
        return this.searchPageSize;
    }

    public long getRequestIntervalMs() {
        return this.requestIntervalMs;
    }

    public long getSearchDebounceMs() {
        return this.searchDebounceMs;
    }

    public boolean isCacheEnabled() {
        return this.cacheEnabled;
    }

    public boolean isTemporaryCacheEnabled() {
        return this.temporaryCacheEnabled;
    }

    public boolean shouldClearTemporaryCacheOnExit() {
        return this.clearTemporaryCacheOnExit;
    }

    public PlayMode getPlayMode() {
        return this.playMode;
    }

    public void setPlayMode(PlayMode playMode) {
        this.playMode = playMode == null ? PlayMode.ORDER : playMode;
    }

    public float getVolume() {
        return this.volume;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
    }

    public boolean isDisclaimerAccepted() {
        return this.disclaimerAccepted && this.disclaimerVersion >= CURRENT_DISCLAIMER_VERSION;
    }

    public void acceptDisclaimer() {
        this.disclaimerAccepted = true;
        this.disclaimerVersion = CURRENT_DISCLAIMER_VERSION;
        this.disclaimerAcceptedAt = System.currentTimeMillis();
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) ? object.get(key).getAsBoolean() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int readInt(JsonObject object, String key, int fallback, int min, int max) {
        try {
            return clamp(object.has(key) ? object.get(key).getAsInt() : fallback, min, max);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long readLong(JsonObject object, String key, long fallback, long min, long max) {
        try {
            long value = object.has(key) ? object.get(key).getAsLong() : fallback;
            return Math.max(min, Math.min(max, value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float readFloat(JsonObject object, String key, float fallback, float min, float max) {
        try {
            float value = object.has(key) ? object.get(key).getAsFloat() : fallback;
            return Math.max(min, Math.min(max, value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String readString(JsonObject object, String key, String fallback) {
        try {
            String value = object.has(key) ? object.get(key).getAsString().trim() : fallback;
            return value.isEmpty() ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static List<String> readStringList(JsonObject object, String key, List<String> fallback) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return new ArrayList<>(fallback);
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : object.getAsJsonArray(key)) {
            try {
                String value = element.getAsString().trim();
                if (!value.isEmpty() && !values.contains(value)) {
                    values.add(value);
                }
            } catch (Exception ignored) {
            }
        }
        return values.isEmpty() ? new ArrayList<>(fallback) : values;
    }

    private static PlayMode readMode(JsonObject object, String key, PlayMode fallback) {
        try {
            return object.has(key) ? PlayMode.valueOf(object.get(key).getAsString()) : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
