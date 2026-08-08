package shit.zen.value.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import shit.zen.value.Value;
import shit.zen.value.ValueCondition;
import shit.zen.value.ValueType;

public class MultiEnumValue extends Value<List<String>> {
    private final List<String> options;

    public MultiEnumValue(String displayName, String... options) {
        super(Value.normalizeId(displayName), displayName, "", new ArrayList<>(), ValueType.MULTI_ENUM);
        this.options = Arrays.asList(options);
        this.metadata("options", this.options);
        this.alias(displayName);
    }

    public List<String> getOptions() {
        return this.options;
    }

    public MultiEnumValue withDefaults(String... defaults) {
        this.setValue(new ArrayList<>(Arrays.asList(defaults)));
        return this;
    }

    public MultiEnumValue withVisibility(ValueCondition visibility) {
        this.visibleWhen(visibility);
        return this;
    }

    public boolean isSelected(String option) {
        return this.getValue().contains(option);
    }
}
