package shit.zen.command.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import shit.zen.ZenClient;
import shit.zen.command.Command;
import shit.zen.modules.impl.world.Protocol;
import shit.zen.utils.misc.ChatUtil;

public final class HwidCommand extends Command {
    private static final String[] ACTIONS = {"random", "new", "load", "list", "help"};

    public HwidCommand() {
        super("hwid", new String[]{"hardwareid"});
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            printHelp();
            return;
        }
        Protocol protocol = ZenClient.getInstance().getModuleManager().initProtocol();
        String action = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "random" -> random(protocol, args);
                case "new" -> create(protocol, args);
                case "load" -> load(protocol, args);
                case "list" -> list(protocol, args);
                default -> printHelp();
            }
        } catch (IllegalArgumentException | IllegalStateException error) {
            ChatUtil.print(error.getMessage());
        }
    }

    @Override
    public String[] onTab(String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return Arrays.stream(ACTIONS)
                .filter(action -> action.startsWith(prefix))
                .toArray(String[]::new);
        }
        if (args.length == 2 && "load".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            try {
                return protocol().listHwidProfiles().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toArray(String[]::new);
            } catch (RuntimeException ignored) {
                return new String[0];
            }
        }
        return new String[0];
    }

    private void random(Protocol protocol, String[] args) {
        if (args.length != 1) {
            ChatUtil.print("Usage: .hwid random");
            return;
        }
        protocol.useRandomHwid();
        ChatUtil.print("Random HWID prepared for the next protocol startup (memory only).");
    }

    private void create(Protocol protocol, String[] args) {
        if (args.length != 2) {
            ChatUtil.print("Usage: .hwid new <name>");
            return;
        }
        String name = protocol.createHwidProfile(args[1]);
        ChatUtil.print("HWID profile created and selected: " + name
            + " (applies on next protocol startup).");
    }

    private void load(Protocol protocol, String[] args) {
        if (args.length != 2) {
            ChatUtil.print("Usage: .hwid load <name>");
            printProfiles(protocol.listHwidProfiles());
            return;
        }
        String name = protocol.loadHwidProfile(args[1]);
        ChatUtil.print("HWID profile selected: " + name
            + " (applies on next protocol startup).");
    }

    private void list(Protocol protocol, String[] args) {
        if (args.length != 1) {
            ChatUtil.print("Usage: .hwid list");
            return;
        }
        printProfiles(protocol.listHwidProfiles());
    }

    private void printProfiles(List<String> profiles) {
        ChatUtil.print(profiles.isEmpty()
            ? "No saved HWID profiles. Use .hwid new <name> to create one."
            : "HWID profiles: " + String.join(", ", profiles));
    }

    private void printHelp() {
        ChatUtil.print("Usage: .hwid random | new <name> | load <name> | list");
    }

    private Protocol protocol() {
        return ZenClient.getInstance().getModuleManager().initProtocol();
    }
}
