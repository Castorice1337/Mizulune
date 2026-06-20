package shit.zen.manager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.ZenClient;
import shit.zen.config.Config;
import shit.zen.config.json.JsonValuesConfig;

public class ConfigManager {
    public static final Logger LOGGER = LogManager.getLogger("ConfigManager");
    public static final File CONFIG_DIR = new File(ZenClient.configDir, "configs");
    private static final long SAVE_DEBOUNCE_MS = 500L;
    private final List<Config> loadConfigs;
    private final List<Config> saveConfigs;
    private final ScheduledExecutorService saveExecutor;
    private final Object debounceLock = new Object();
    private final Object writeLock = new Object();
    private ScheduledFuture<?> pendingSave;

    public ConfigManager() {
        this.loadConfigs = new ArrayList<>();
        this.saveConfigs = new ArrayList<>();
        this.saveExecutor = Executors.newSingleThreadScheduledExecutor(new SaveThreadFactory());
        if (!CONFIG_DIR.exists() && CONFIG_DIR.mkdir()) {
            LOGGER.info("Created config directory");
        }
        JsonValuesConfig jsonValuesConfig = new JsonValuesConfig();
        this.loadConfigs.add(jsonValuesConfig);
        this.saveConfigs.add(jsonValuesConfig);
    }

    public void loadAll() {
        for (Config config : this.loadConfigs) {
            try {
                File file = config.getFile();
                if (file.exists()) {
                    readConfigFile(config, file);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load config " + config.getName(), e);
            }
        }
    }

    private void readConfigFile(Config config, File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
            config.read(reader);
        }
    }

    public void saveAll() {
        this.cancelPendingSave();
        this.saveAllNow();
    }

    public void requestSave() {
        synchronized (this.debounceLock) {
            if (this.pendingSave != null) {
                this.pendingSave.cancel(false);
            }
            this.pendingSave = this.saveExecutor.schedule(this::runDebouncedSave, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }

    public void flushPendingSave() {
        this.cancelPendingSave();
        this.saveAllNow();
    }

    public void shutdown() {
        this.flushPendingSave();
        this.saveExecutor.shutdownNow();
    }

    public static void requestSaveIfReady() {
        if (ZenClient.isReady() && ZenClient.instance != null && ZenClient.instance.getConfigManager() != null) {
            ZenClient.instance.getConfigManager().requestSave();
        }
    }

    private void runDebouncedSave() {
        synchronized (this.debounceLock) {
            this.pendingSave = null;
        }
        this.saveAllNow();
    }

    private void cancelPendingSave() {
        synchronized (this.debounceLock) {
            if (this.pendingSave != null) {
                this.pendingSave.cancel(false);
                this.pendingSave = null;
            }
        }
    }

    private void saveAllNow() {
        synchronized (this.writeLock) {
            for (Config config : this.saveConfigs) {
                this.saveConfig(config);
            }
            LOGGER.info("Saved all configs");
        }
    }

    private void saveConfig(Config config) {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(config.getFile().toPath()), StandardCharsets.UTF_8))) {
            config.save(writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save config " + config.getName(), e);
        }
    }

    private static final class SaveThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Mizulune-ConfigSave-" + this.counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
