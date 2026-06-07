package shit.zen.manager;

import java.util.HashMap;
import java.util.Map;
import shit.zen.ZenClient;
import shit.zen.command.Command;
import shit.zen.command.impl.BindCommand;
import shit.zen.command.impl.ConfigCommand;
import shit.zen.command.impl.LanguageCommand;
import shit.zen.command.impl.ToggleCommand;
import shit.zen.event.impl.ChatEvent;
import shit.zen.utils.misc.ChatUtil;
import shit.zen.event.EventTarget;

public class CommandManager {
    public static final String PREFIX = ".";
    public final Map<String, Command> aliasMap = new HashMap<>();

    public CommandManager() {
        ZenClient.getInstance().getEventBus().register(this);
    }

    public void initCommands() {
        this.registerCommand(new BindCommand());
        this.registerCommand(new ConfigCommand());
        this.registerCommand(new LanguageCommand());
        this.registerCommand(new ToggleCommand());
    }

    private void registerCommand(Command command) {
        this.aliasMap.put(command.getPrefix().toLowerCase(), command);
        for (String string : command.getAliases()) {
            this.aliasMap.put(string.toLowerCase(), command);
        }
    }

    @EventTarget
    public void onChat(ChatEvent chatEvent) {
        if (chatEvent.getMessage().startsWith(PREFIX)) {
            chatEvent.setCancelled(true);
            String string = chatEvent.getMessage().substring(PREFIX.length());
            String[] stringArray = string.split(" ");
            if (stringArray.length < 1) {
                ChatUtil.print("Unknown command");
                return;
            }
            String alias = stringArray[0].toLowerCase();
            Command command = this.aliasMap.get(alias);
            if (command == null) {
                ChatUtil.print("Unknown command");
                return;
            }
            String[] args = new String[stringArray.length - 1];
            System.arraycopy(stringArray, 1, args, 0, args.length);
            command.onCommand(args);
        }
    }
}
