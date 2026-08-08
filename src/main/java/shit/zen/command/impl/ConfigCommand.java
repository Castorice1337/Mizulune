package shit.zen.command.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import shit.zen.ZenClient;
import shit.zen.command.Command;
import shit.zen.manager.ConfigManager;
import shit.zen.utils.misc.ChatUtil;

public class ConfigCommand extends Command {
    private static final String[] CORE_COMMANDS = {"load", "save", "folder", "list", "help"};

    public ConfigCommand() {
        super("config", new String[]{"cfg"});
    }

    @Override
    public void onCommand(String[] stringArray) {
        if (stringArray.length == 0 || "help".equalsIgnoreCase(stringArray[0])) {
            this.printHelp();
            return;
        }
        ConfigManager configManager = ZenClient.getInstance().getConfigManager();
        String action = stringArray[0].toLowerCase();
        try {
            switch (action) {
                case "save" -> this.save(configManager, stringArray);
                case "load" -> this.load(configManager, stringArray);
                case "list" -> this.listProfiles(configManager);
                case "folder" -> this.openFolder();
                default -> this.printHelp();
            }
        } catch (IllegalArgumentException exception) {
            ChatUtil.print(exception.getMessage());
        }
    }

    @Override
    public String[] onTab(String[] stringArray) {
        if (stringArray.length <= 1) {
            String prefix = stringArray.length == 0 ? "" : stringArray[0].toLowerCase();
            return this.commandSuggestions(prefix);
        }
        ConfigManager configManager = ZenClient.getInstance().getConfigManager();
        String action = stringArray[0].toLowerCase();
        String prefix = stringArray[stringArray.length - 1].toLowerCase();
        if ("load".equals(action) || "save".equals(action)) {
            return configManager.listProfiles().stream()
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toArray(String[]::new);
        }
        return new String[0];
    }

    private void save(ConfigManager configManager, String[] args) {
        if (args.length == 1) {
            ChatUtil.print(configManager.saveAll()
                    ? "Config saved: settings.json"
                    : "Config save failed: config is not loaded yet.");
            return;
        }
        if (args.length == 2) {
            String name = ConfigManager.normalizeProfileName(args[1]);
            ChatUtil.print(configManager.saveProfile(args[1])
                    ? "Config profile saved: " + name
                    : (configManager.canSave()
                            ? "Failed to save config profile: " + name
                            : "Config profile save failed: config is not loaded yet."));
            return;
        }
        ChatUtil.print("Usage: .config save [name]");
    }

    private void load(ConfigManager configManager, String[] args) {
        if (args.length != 2) {
            ChatUtil.print("Usage: .config load <name>");
            this.printProfileListSummary(configManager);
            return;
        }
        String name = ConfigManager.normalizeProfileName(args[1]);
        boolean exists = configManager.listProfiles().contains(name);
        ChatUtil.print(configManager.loadProfile(name)
                ? "Config profile loaded: " + name
                : (exists ? "Failed to load config profile: " + name : "Config profile not found: " + name));
    }

    private void listProfiles(ConfigManager configManager) {
        this.printProfileListSummary(configManager);
    }

    private void openFolder() {
        try {
            ConfigManager.ensureConfigDirectories();
            new ProcessBuilder("explorer", ConfigManager.CONFIG_DIR.getAbsolutePath()).start();
            ChatUtil.print("Opened config folder: " + ConfigManager.CONFIG_DIR.getAbsolutePath());
        } catch (IOException ignored) {
            ChatUtil.print("Failed to open config folder.");
        }
    }

    private void printHelp() {
        ChatUtil.print("Usage: .config save [name] | load <name> | list | folder");
    }

    private void printProfileListSummary(ConfigManager configManager) {
        List<String> profiles = configManager.listProfiles();
        ChatUtil.print(profiles.isEmpty()
                ? "No config profiles. Use .config save <name> to create one."
                : "Config profiles: " + String.join(", ", profiles));
    }

    private String[] commandSuggestions(String prefix) {
        return Arrays.stream(CORE_COMMANDS)
                .filter(option -> option.startsWith(prefix))
                .toArray(String[]::new);
    }
}
