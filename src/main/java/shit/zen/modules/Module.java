package shit.zen.modules;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import lombok.Getter;
import lombok.Generated;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.modules.impl.render.Notification;
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
    private final ValueGroup valueTree;
    private Value<Boolean> hideInModuleList;
    private static final String REGISTER_FAIL_MSG = "Failed to register value for module ";

    protected Module(String name, Category category) {
        this.id = Value.normalizeId(this.getClass().getSimpleName());
        this.name = name;
        this.category = category;
        this.keyCode = 0;
        this.bind = new KeyBind(this.keyCode);
        this.valueTree = new ValueGroup(this.id, name);
    }

    protected Module(String name, Category category, int keyCode) {
        this.id = Value.normalizeId(this.getClass().getSimpleName());
        this.name = name;
        this.category = category;
        this.keyCode = keyCode;
        this.bind = new KeyBind(this.keyCode);
        this.valueTree = new ValueGroup(this.id, name);
    }

    public void setKey(int keyCode) {
        this.keyCode = keyCode;
        this.bind.setKey(keyCode);
    }

    public void registerSettings() {
        this.valueTree.clearChildren();
        ValueGroup commonGroup = this.valueTree.group("common", "Common");
        this.hideInModuleList = commonGroup.bool("hide_in_module_list", "Hide in ModuleList", this.defaultHiddenInModuleList())
                .alias("Hide in ModuleList")
                .alias("Hide in modulelist")
                .alias("Hidden");
        this.configureValueTree(this.valueTree);
        ValueGroup reflectedGroup = null;
        Set<Value<?>> seenValues = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Class<?> clazz = this.getClass(); clazz != null && clazz != Module.class && Module.class.isAssignableFrom(clazz); clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    Object value = field.get(Modifier.isStatic(field.getModifiers()) ? null : this);
                    if (value instanceof Value<?> moduleValue) {
                        if (moduleValue.getParent() != null || !seenValues.add(moduleValue)) {
                            continue;
                        }
                        if (reflectedGroup == null) {
                            reflectedGroup = this.valueTree.group("settings", "Settings");
                        }
                        reflectedGroup.add(moduleValue);
                    }
                } catch (IllegalAccessException ex) {
                    System.out.println(REGISTER_FAIL_MSG + this.getName() + "!");
                }
            }
        }
    }

    protected void configureValueTree(ValueGroup root) {
    }

    protected boolean defaultHiddenInModuleList() {
        return false;
    }

    public boolean isHiddenInModuleList() {
        return this.hideInModuleList != null && Boolean.TRUE.equals(this.hideInModuleList.getValue());
    }

    public Value<Boolean> getHideInModuleListValue() {
        return this.hideInModuleList;
    }

    public String getSuffix() {
        return "";
    }

    protected static String formatSuffixNumber(Number number) {
        if (number == null) {
            return "";
        }
        double value = number.doubleValue();
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return Integer.toString((int)Math.round(value));
        }
        String formatted = String.format(Locale.US, "%.1f", value);
        while (formatted.contains(".") && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
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
