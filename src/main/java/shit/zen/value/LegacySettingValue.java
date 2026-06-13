package shit.zen.value;

import java.util.Arrays;
import java.util.List;
import shit.zen.settings.Setting;
import shit.zen.settings.impl.BooleanSetting;
import shit.zen.settings.impl.ModeSetting;
import shit.zen.settings.impl.MultiSelectSetting;
import shit.zen.settings.impl.NumberSetting;

public final class LegacySettingValue<T> extends Value<T> {
    private final Setting<T> setting;

    private LegacySettingValue(String id, Setting<T> setting, ValueType type) {
        super(id, setting.getName(), "", setting.getValue(), type);
        this.setting = setting;
        this.alias(setting.getName());
        this.visibleWhen(() -> setting.getVisibility() == null || setting.getVisibility().displayable());
        if (setting instanceof NumberSetting numberSetting) {
            this.range(numberSetting.getMin(), numberSetting.getMax(), numberSetting.getStep());
        } else if (setting instanceof ModeSetting modeSetting) {
            this.metadata("options", Arrays.asList(modeSetting.getModes()));
        } else if (setting instanceof MultiSelectSetting multiSelectSetting) {
            this.metadata("options", multiSelectSetting.getOptions());
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Value<?> from(Setting<?> setting) {
        ValueType type;
        if (setting instanceof BooleanSetting) {
            type = ValueType.BOOLEAN;
        } else if (setting instanceof NumberSetting numberSetting) {
            type = isIntegral(numberSetting) ? ValueType.INTEGER : ValueType.DECIMAL;
        } else if (setting instanceof ModeSetting) {
            type = ValueType.ENUM;
        } else if (setting instanceof MultiSelectSetting) {
            type = ValueType.MULTI_ENUM;
        } else {
            type = ValueType.TEXT;
        }
        return new LegacySettingValue(Value.normalizeId(setting.getName()), (Setting)setting, type);
    }

    private static boolean isIntegral(NumberSetting setting) {
        return setting.getValue() instanceof Integer || setting.getValue() instanceof Long;
    }

    @Override
    public T getValue() {
        return this.setting.getValue();
    }

    @Override
    public void setValue(T value) {
        this.setting.setValue(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setRawValue(Object value) {
        this.setValue((T)value);
    }

    public Setting<T> getSetting() {
        return this.setting;
    }
}
