package shit.zen.manager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.ZenClient;
import shit.zen.config.Config;
import shit.zen.config.json.JsonValuesConfig;

public class ConfigManager {
    public static final Logger LOGGER = LogManager.getLogger("ConfigManager");
    public static final File CONFIG_DIR = new File(ZenClient.configDir, "configs");
    public static final File PROFILES_DIR = new File(CONFIG_DIR, "profiles");
    private static final File BACKUPS_DIR = new File(CONFIG_DIR, "backups");
    private static final long SAVE_DEBOUNCE_MS = 500L;
    private static final int MAX_BACKUPS = 30;
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private final List<Config> loadConfigs;
    private final List<Config> saveConfigs;
    private final ScheduledExecutorService saveExecutor;
    private final Object debounceLock = new Object();
    private final Object ioLock = new Object();
    private ScheduledFuture<?> pendingSave;
    private volatile boolean loaded;
    private volatile boolean loading;

    public ConfigManager() {
        this.loadConfigs = new ArrayList<>();
        this.saveConfigs = new ArrayList<>();
        this.saveExecutor = Executors.newSingleThreadScheduledExecutor(new SaveThreadFactory());
        ensureDirectory(CONFIG_DIR);
        ensureDirectory(PROFILES_DIR);
        ensureDirectory(BACKUPS_DIR);
        JsonValuesConfig jsonValuesConfig = new JsonValuesConfig();
        this.loadConfigs.add(jsonValuesConfig);
        this.saveConfigs.add(jsonValuesConfig);
    }

    public boolean loadAll() {
        synchronized (this.ioLock) {
            return this.loadAllLocked();
        }
    }

    private boolean loadAllLocked() {
        this.loading = true;
        boolean success = true;
        try {
            for (Config config : this.loadConfigs) {
                try {
                    File file = config.getFile();
                    if (file.exists()) {
                        readConfigFile(config, file);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load config " + config.getName(), e);
                    success = false;
                }
            }
        } finally {
            this.loaded = success;
            this.loading = false;
        }
        if (success) {
            LOGGER.info("Loaded all configs");
        }
        return success;
    }

    private void readConfigFile(Config config, File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
            config.read(reader);
        }
    }

    public boolean saveAll() {
        this.cancelPendingSave();
        return this.saveAllNow();
    }

    public void requestSave() {
        if (!this.canSave()) {
            LOGGER.debug("Ignoring config save request before configs are loaded");
            return;
        }
        synchronized (this.debounceLock) {
            if (this.pendingSave != null) {
                this.pendingSave.cancel(false);
            }
            this.pendingSave = this.saveExecutor.schedule(this::runDebouncedSave, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }

    public boolean flushPendingSave() {
        if (!this.canSave()) {
            this.cancelPendingSave();
            LOGGER.info("Skipping config flush before configs are loaded");
            return false;
        }
        this.cancelPendingSave();
        return this.saveAllNow();
    }

    public void shutdown() {
        this.flushPendingSave();
        this.saveExecutor.shutdownNow();
    }

    public static void requestSaveIfReady() {
        if (ZenClient.isReady() && ZenClient.instance != null && ZenClient.instance.getConfigManager() != null) {
            ConfigManager configManager = ZenClient.instance.getConfigManager();
            if (configManager.canSave()) {
                configManager.requestSave();
            }
        }
    }

    public boolean canSave() {
        return this.loaded && !this.loading;
    }

    public File getCanonicalFile() {
        return new File(CONFIG_DIR, "settings.json");
    }

    public List<String> listProfiles() {
        ensureDirectory(PROFILES_DIR);
        return listProfileNames(PROFILES_DIR.toPath());
    }

    public boolean saveProfile(String rawName) {
        String name = normalizeProfileName(rawName);
        if (!this.saveAll()) {
            return false;
        }
        synchronized (this.ioLock) {
            try {
                Path profile = profilePath(name);
                ensureDirectory(PROFILES_DIR);
                Files.copy(this.getCanonicalFile().toPath(), profile, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Saved config profile {}", name);
                return true;
            } catch (IOException exception) {
                LOGGER.error("Failed to save config profile " + name, exception);
                return false;
            }
        }
    }

    public boolean loadProfile(String rawName) {
        String name = normalizeProfileName(rawName);
        this.cancelPendingSave();
        synchronized (this.ioLock) {
            Path profile = profilePath(name);
            if (!Files.isRegularFile(profile)) {
                LOGGER.warn("Config profile {} does not exist", name);
                return false;
            }
            try {
                validateJsonFile(profile);
                this.createBackupLocked();
                Files.copy(profile, this.getCanonicalFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
                return this.loadAllLocked();
            } catch (IOException exception) {
                LOGGER.error("Failed to load config profile " + name, exception);
                return false;
            }
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

    private boolean saveAllNow() {
        if (!this.canSave()) {
            LOGGER.warn("Refusing to save configs before a successful load");
            return false;
        }
        synchronized (this.ioLock) {
            boolean success = true;
            for (Config config : this.saveConfigs) {
                success &= this.saveConfig(config);
            }
            if (success) {
                LOGGER.info("Saved all configs");
            }
            return success;
        }
    }

    private boolean saveConfig(Config config) {
        Path target = config.getFile().toPath();
        Path parent = target.getParent();
        Path temp = parent.resolve(target.getFileName().toString() + ".tmp");
        try {
            Files.createDirectories(parent);
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(Files.newOutputStream(temp), StandardCharsets.UTF_8))) {
                config.save(writer);
            }
            if (config.getName().endsWith(".json")) {
                validateJsonFile(temp);
            }
            if (Files.exists(target)) {
                this.createBackupLocked();
            }
            moveReplacing(temp, target);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to save config " + config.getName(), e);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
            return false;
        }
    }

    private Path createBackupLocked() throws IOException {
        Path source = this.getCanonicalFile().toPath();
        if (!Files.isRegularFile(source)) {
            return null;
        }
        ensureDirectory(BACKUPS_DIR);
        Path backup = BACKUPS_DIR.toPath().resolve("settings-" + LocalDateTime.now().format(BACKUP_TIMESTAMP) + ".json");
        Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
        this.pruneBackupsLocked();
        return backup;
    }

    private void pruneBackupsLocked() throws IOException {
        if (!BACKUPS_DIR.isDirectory()) {
            return;
        }
        List<Path> backups;
        try (var stream = Files.list(BACKUPS_DIR.toPath())) {
            backups = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.<Path>comparingLong(path -> path.toFile().lastModified()).reversed())
                    .toList();
        }
        for (int i = MAX_BACKUPS; i < backups.size(); i++) {
            Files.deleteIfExists(backups.get(i));
        }
    }

    public static List<String> listProfileNames(Path directory) {
        return listJsonNamesInDirectory(directory);
    }

    private static List<String> listJsonNamesInDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .sorted()
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .toList();
        } catch (IOException exception) {
            LOGGER.error("Failed to list configs in " + directory, exception);
            return List.of();
        }
    }

    public static void ensureConfigDirectories() {
        ensureDirectory(CONFIG_DIR);
        ensureDirectory(PROFILES_DIR);
        ensureDirectory(BACKUPS_DIR);
    }

    public static String normalizeProfileName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - ".json".length());
        }
        validateSafeName(name, "profile");
        return name;
    }

    private static void validateSafeName(String name, String kind) {
        if (name == null || name.isBlank()
                || ".".equals(name)
                || "..".equals(name)
                || name.contains("..")
                || name.contains("/")
                || name.contains("\\")
                || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid " + kind + " name: " + name);
        }
    }

    private static Path profilePath(String name) {
        return PROFILES_DIR.toPath().resolve(name + ".json").normalize();
    }

    private static void validateJsonFile(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonParser.parseReader(reader);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid JSON file: " + path, exception);
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void ensureDirectory(File directory) {
        if (!directory.exists() && directory.mkdirs()) {
            LOGGER.info("Created config directory {}", directory);
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
