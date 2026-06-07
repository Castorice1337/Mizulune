package shit.zen.command.impl;

import shit.zen.ZenClient;
import shit.zen.command.Command;
import shit.zen.exception.ModuleNotFoundException;
import shit.zen.modules.Module;
import shit.zen.utils.misc.ChatUtil;

public class ToggleCommand
extends Command {
    public ToggleCommand() {
        super("toggle", new String[]{"t"});
    }

    @Override
    public void onCommand(String[] stringArray) {
        if (stringArray.length == 1) {
            String string = stringArray[0];
            try {
                Module module = ZenClient.getInstance().getModuleManager().getModule(string);
                if (module != null) {
                    module.setEnabled(!module.isEnabled());
                    ChatUtil.print("Toggled " + module.getName() + ".");
                } else {
                    ChatUtil.print("Invalid module.");
                }
            } catch (ModuleNotFoundException moduleNotFoundException) {
                ChatUtil.print("Invalid module.");
            }
        }
    }

    @Override
    public String[] onTab(String[] stringArray) {
        return ZenClient.getInstance().getModuleManager().getModules().stream().map(Module::getName).filter(string -> string.toLowerCase().startsWith(stringArray.length == 0 ? "" : stringArray[0].toLowerCase())).toArray(String[]::new);
    }
}