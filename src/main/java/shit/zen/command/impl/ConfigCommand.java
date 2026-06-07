package shit.zen.command.impl;

import java.io.IOException;
import shit.zen.ZenClient;
import shit.zen.command.Command;
import shit.zen.manager.ConfigManager;
import shit.zen.utils.misc.ChatUtil;

public class ConfigCommand extends Command {
    public ConfigCommand() {
        super("config", new String[]{"cfg"});
    }

    @Override
    public void onCommand(String[] stringArray) {
        if (stringArray.length == 1) {
            switch (stringArray[0]) {
                case "reload":
                    ZenClient.getInstance().getConfigManager().loadAll();
                    ChatUtil.print("Config reloaded!");
                    break;
                case "folder":
                    try {
                        Runtime.getRuntime().exec("explorer " + ConfigManager.CONFIG_DIR.getAbsolutePath());
                    } catch (IOException ignored) {
                    }
                    break;
            }
        } else {
            ChatUtil.print("Usage: config reload/folder");
        }
    }

    @Override
    public String[] onTab(String[] stringArray) {
        return new String[0];
    }
}
