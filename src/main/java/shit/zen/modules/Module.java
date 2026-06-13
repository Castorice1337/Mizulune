package shit.zen.modules;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Generated;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.modules.impl.render.Notification;
import shit.zen.settings.Setting;
import shit.zen.value.LegacySettingValue;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;

public abstract class Module
extends ClientBase {
    @Getter
    private final String id;
    @Getter
    private final String name;
    @Getter
    private final Category category;
    private int keyCode;
    @Getter
    private final KeyBind bind;
    @Getter
    private boolean enabled;
    @Getter
    private final List<Setting<?>> settings;
    @Getter
    private final ValueGroup valueTree;
    private static final String REGISTER_FAIL_MSG = "Failed to register value for module ";

    protected Module(String name, Category category) {
        this.id = Value.normalizeId(this.getClass().getSimpleName());
        this.name = name;
        this.category = category;
        this.keyCode = 0;
        this.bind = new KeyBind(this.keyCode);
        this.settings = new ArrayList<>();
        this.valueTree = new ValueGroup(this.id, name);
    }

    protected Module(String name, Category category, int keyCode) {
        this.id = Value.normalizeId(this.getClass().getSimpleName());
        this.name = name;
        this.category = category;
        this.keyCode = keyCode;
        this.bind = new KeyBind(this.keyCode);
        this.settings = new ArrayList<>();
        this.valueTree = new ValueGroup(this.id, name);
    }

    public void setKey(int keyCode) {
        this.keyCode = keyCode;
        this.bind.setKey(keyCode);
    }

    public void addSetting(Setting<?> setting) {
        this.settings.add(setting);
    }

    public void registerSettings() {
        this.settings.clear();
        this.valueTree.clearChildren();
        this.configureValueTree(this.valueTree);
        ValueGroup legacyGroup = null;
        for (Class<?> clazz = this.getClass(); clazz != null && Module.class.isAssignableFrom(clazz); clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    Object value;
                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    if (!((value = field.get(this)) instanceof Setting)) continue;
                    Setting<?> setting = (Setting<?>)value;
                    this.addSetting(setting);
                    if (legacyGroup == null) {
                        legacyGroup = this.valueTree.group("legacy", "Legacy Settings");
                    }
                    legacyGroup.add(LegacySettingValue.from(setting));
                } catch (IllegalAccessException ex) {
                    System.out.println(REGISTER_FAIL_MSG + this.getName() + "!");
                }
            }
        }
    }

    protected void configureValueTree(ValueGroup root) {
    }

    public Value<?> findValue(String pathOrName) {
        return this.valueTree.findByPath(pathOrName)
                .or(() -> this.valueTree.findByAliasOrDisplayName(pathOrName))
                .orElse(null);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;

        if (enabled) {
            ZenClient.getInstance().getEventBus().register(this);
            this.onEnable();
        } else {
            this.onDisable();
            ZenClient.getInstance().getEventBus().unregister(this);
        }
    }

    public void toggle() {
        this.setEnabled(!this.isEnabled());
    }

    public void setEnabledFromUser(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.setEnabled(enabled);
        Notification.submitModuleToggle(this, enabled);
    }

    public void toggleFromUser() {
        this.setEnabledFromUser(!this.isEnabled());
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    @Generated
    public int getKey() {
        return this.keyCode;
    }

}
