package shit.zen;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.lang.reflect.Field;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import shit.zen.event.EventBus;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.TickEvent;
import shit.zen.gui.IntroAnimation;
import shit.zen.manager.CommandManager;
import shit.zen.manager.ConfigManager;
import shit.zen.manager.HudManager;
import shit.zen.manager.LagManager;
import shit.zen.manager.ModuleManager;
import shit.zen.manager.TargetManager;
import shit.zen.music.MusicService;
import shit.zen.modules.impl.world.Protocol;
import shit.zen.utils.game.PlayerPositionHold;
import shit.zen.utils.rotation.RotationHandler;

@Getter
@Setter
public class ZenClient extends ClientBase {
    @Getter
    public static ZenClient instance;
    public static final String CLIENT_NAME = "Mizulune Client";
    public static final String CLIENT_SHORT_NAME = "Mizulune";
    public static final String CLIENT_CHINESE_NAME = "水月蝶";
    public static final String CLIENT_ABBR = "MZL";
    public static final String VERSION = "1.2";
    public static float serverTickRate;
    public static boolean isReady;
    public static boolean isMCPMapped;
    public static String configDir = System.getProperty("user.home") + File.separator + ".mizulune";
    public static String username = "";

    private static final String[] CLOUD_ASSET_NAMES = { "panel.png", "ptr.png", "lie.wav", "truth.wav" };

    private EventBus eventBus;
    private RotationHandler rotationHandler;
    private ModuleManager moduleManager;
    private CommandManager commandManager;
    private ConfigManager configManager;
    private HudManager hudManager;
    private LagManager lagManager;
    private TargetManager targetManager;
    private MusicService musicService;
    private Protocol protocolModule;
    private int reconnectAttempts;

    public ZenClient() {
        if (instance == null) {
            instance = this;
            this.init();
        }
    }

    /** Loader-neutral one-shot bootstrap used by Forge/ASM, DLL injection and Fabric. */
    public static synchronized ZenClient bootstrap() {
        if (instance == null) {
            new ZenClient();
        }
        return instance;
    }

    private void init() {
        try {
            username = resolveDisplayName();
            File dir = new File(configDir);
            if (!dir.exists() && !dir.mkdirs()) {
                logger.warn("Failed to create config directory at {}", configDir);
            }
            mc = getMcInstance();
            this.eventBus = new EventBus();
            this.rotationHandler = new RotationHandler();
            this.eventBus.register(this.rotationHandler);
            this.eventBus.register(new PlayerPositionHold());
            this.moduleManager = new ModuleManager();
            this.hudManager = new HudManager();
            this.commandManager = new CommandManager();
            this.configManager = new ConfigManager();
            this.musicService = new MusicService();
            this.extractCloudAssets();
            this.lagManager = new LagManager();
            this.targetManager = new TargetManager();
            this.eventBus.register(this.hudManager);
            this.eventBus.register(this.lagManager);
            this.eventBus.register(this.targetManager);
            this.eventBus.register(this);
            this.commandManager.initCommands();
            this.eventBus.register(new IntroAnimation());
            this.protocolModule = this.moduleManager.initProtocol();
            this.configManager.loadAll();
            this.protocolModule.completeBootstrapConfiguration();
            isReady = true;
            logger.info("{} v{} initialized.", CLIENT_NAME, VERSION);
        } catch (Throwable throwable) {
            logger.error(throwable.getMessage(), throwable);
        }
    }

    private static String resolveDisplayName() {
        String fallback = System.getProperty("user.name", "Player");
        File profileFile = new File(configDir, "loader-profile.properties");
        if (!profileFile.isFile()) {
            return fallback;
        }
        try (InputStream in = new java.io.FileInputStream(profileFile);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            String displayName = properties.getProperty("displayName", "").trim();
            if (!displayName.isEmpty()) {
                return displayName.length() > 32 ? displayName.substring(0, 32).trim() : displayName;
            }
        } catch (IOException ioException) {
            logger.warn("Failed to read loader profile at {}", profileFile, ioException);
        }
        return fallback;
    }

    /**
     * Refreshes the display name from the same profile file written by the
     * native loader. This keeps the in-game title synchronized when the
     * launcher settings are changed after injection.
     */
    public static void refreshDisplayName() {
        String resolved = resolveDisplayName();
        if (resolved != null && !resolved.isBlank()) {
            username = resolved;
        }
    }

    private boolean moduleInit = false;

    @EventTarget
    public void onTick(TickEvent e) {
        if (isReady() && !moduleInit) {
            moduleInit = true;
            this.moduleManager.initModules();
            this.configManager.loadAll();
        }
    }

    /** Runs even before the general module/event readiness gate. */
    public void tickProtocolBootstrap() {
        Protocol protocol = this.protocolModule;
        if (protocol != null) {
            protocol.bootstrapTick();
        }
    }

    /** Runs the protocol's phase-agnostic ID3 producer and END-only ready work. */
    public void tickProtocolBootstrapEnd() {
        Protocol protocol = this.protocolModule;
        if (protocol != null) protocol.bootstrapTickEnd();
    }

    public static boolean isReady() {
        return instance != null
                && ZenClient.instance.eventBus != null
                && isReady
                && mc != null
                && mc.player != null
                && !username.isEmpty()
                && mc.player.tickCount > 5;
    }

    public void shutdown() {
        isReady = false;
        if (this.protocolModule != null) {
            // Minecraft.close can run after clearLevel or without it on some graceful shutdown paths.
            // The native ID114 lease itself is consumed exactly once even if both callbacks run.
            this.protocolModule.onLoggingOut();
            this.protocolModule.shutdownRuntime();
        }
        if (this.musicService != null) {
            this.musicService.shutdown();
        }
        if (this.configManager != null) {
            this.configManager.shutdown();
        }
    }

    private void extractCloudAssets() {
        File targetDir = ConfigManager.CONFIG_DIR;
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            logger.warn("Failed to create config directory at {}", targetDir);
            return;
        }
        for (String name : CLOUD_ASSET_NAMES) {
            File outFile = new File(targetDir, name);
            if (outFile.exists()) continue;
            try (InputStream in = openCloudAsset(name)) {
                if (in == null) {
                    logger.warn("Cloud asset missing on classpath: {}", name);
                    continue;
                }
                try (OutputStream out = new FileOutputStream(outFile)) {
                    in.transferTo(out);
                }
            } catch (IOException ioException) {
                logger.error("Failed to extract cloud asset {}", name, ioException);
            }
        }
    }

    private static InputStream openCloudAsset(String name) {
        String classpath = "/assets/mizulune/cloud_assets/" + name;
        InputStream is = ZenClient.class.getResourceAsStream(classpath);
        if (is != null) return is;
        String dir = System.getProperty("mizulune.resources");
        if (dir != null) {
            File f = new File(dir, "assets/mizulune/cloud_assets/" + name);
            if (f.isFile()) {
                try {
                    return new java.io.FileInputStream(f);
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    public static Minecraft getMcInstance() {
        Minecraft minecraft = null;
        try {
            Class<?> clazz = Minecraft.class;
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType() != clazz) continue;
                field.setAccessible(true);
                minecraft = (Minecraft) field.get(null);
                field.setAccessible(false);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return minecraft != null ? minecraft : Minecraft.getInstance();
    }

}
