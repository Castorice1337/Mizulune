package shit.zen.modules.impl.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;
import shit.zen.event.impl.ChatReceiveEvent;
import shit.zen.event.impl.DisconnectEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.value.Value;
import shit.zen.value.ValueType;
import shit.zen.value.impl.ModeValue;
import shit.zen.event.EventTarget;

public class NameProtect
extends Module {
    public static NameProtect INSTANCE;
    private static final String DEFAULT_FIXED_NAME = "Player";
    private final ModeValue mode = new ModeValue("Mode", "Fixed", "Random", "Hide").withDefault("Fixed");
    private final Value<String> hideName = new Value<>("hide_name", "Hide Name", "", DEFAULT_FIXED_NAME, ValueType.TEXT)
            .metadata("max_length", 32)
            .alias("Hidden Name")
            .alias("Hide Name")
            .visibleWhen(() -> this.mode.is("Hide"));
    private String cachedRandomName = null;
    private final Random random = new Random();

    public NameProtect() {
        super("NameProtect", Category.RENDER);
        INSTANCE = this;
    }

    @EventTarget
    public void onDisconnect(DisconnectEvent disconnectEvent) {
        if (this.mode.is("Random")) {
            this.cachedRandomName = null;
        }
    }

    public static String replacePlayerName(String string) {
        if (string == null || INSTANCE == null || !INSTANCE.isEnabled() || mc.player == null) {
            return string;
        }
        return INSTANCE.replaceNames(string);
    }

    public static String getProtectedName() {
        if (mc.player == null) {
            return DEFAULT_FIXED_NAME;
        }
        String playerName = mc.player.getName().getString();
        if (INSTANCE == null || !INSTANCE.isEnabled()) {
            return playerName;
        }
        String protectedName = INSTANCE.replacementName();
        if (protectedName != null && !protectedName.equals(playerName)) {
            return protectedName;
        }
        return playerName;
    }

    private String replaceNames(String text) {
        String replacement = this.replacementName();
        if (replacement == null || replacement.isEmpty()) {
            return text;
        }
        if (this.mode.is("Hide")) {
            String result = text;
            for (String playerName : this.collectPlayerNames()) {
                if (!playerName.equals(replacement) && result.contains(playerName)) {
                    result = StringUtils.replace(result, playerName, replacement);
                }
            }
            return result;
        }
        String playerName = mc.player.getName().getString();
        if (!replacement.equals(playerName) && text.contains(playerName)) {
            return StringUtils.replace(text, playerName, replacement);
        }
        return text;
    }

    private String replacementName() {
        if (this.mode.is("Hide")) {
            return this.hideName();
        }
        if (this.mode.is("Random")) {
            return this.generateRandomName();
        }
        return DEFAULT_FIXED_NAME;
    }

    private String hideName() {
        String value = this.hideName.getValue();
        if (value == null || value.isBlank()) {
            return DEFAULT_FIXED_NAME;
        }
        return value.trim();
    }

    private List<String> collectPlayerNames() {
        Set<String> names = new LinkedHashSet<>();
        if (mc.player != null) {
            this.addPlayerName(names, mc.player.getName().getString());
        }
        if (mc.getConnection() != null) {
            for (PlayerInfo playerInfo : mc.getConnection().getOnlinePlayers()) {
                this.addPlayerName(names, playerInfo.getProfile().getName());
            }
        }
        return names.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private void addPlayerName(Set<String> names, String name) {
        if (name != null && !name.isBlank()) {
            names.add(name);
        }
    }

    private String generateRandomName() {
        if (mc.getConnection() == null) {
            return null;
        }
        ArrayList<PlayerInfo> arrayList = new ArrayList<>(mc.getConnection().getOnlinePlayers());
        ArrayList<String> arrayList2 = new ArrayList<>();
        String string = mc.player.getName().getString();
        for (PlayerInfo playerInfo : arrayList) {
            String string2 = playerInfo.getProfile().getName();
            if (string2.equals(string)) continue;
            arrayList2.add(string2);
        }
        if (arrayList2.isEmpty()) {
            return null;
        }
        if (this.cachedRandomName == null || !arrayList2.contains(this.cachedRandomName)) {
            this.cachedRandomName = arrayList2.get(this.random.nextInt(arrayList2.size()));
        }
        return this.cachedRandomName;
    }

    @EventTarget
    public void onChatReceive(ChatReceiveEvent chatReceiveEvent) {
        chatReceiveEvent.setComponent(Component.literal(NameProtect.replacePlayerName(chatReceiveEvent.getComponent().getString())));
    }
}
