package shit.zen.value.impl;

import java.util.Arrays;
import shit.zen.value.Value;
import shit.zen.value.ValueCondition;
import shit.zen.value.ValueType;

public class ModeValue extends Value<String> {
    private final String[] modes;

    public ModeValue(String displayName, String... modes) {
        super(Value.normalizeId(displayName), displayName, "", modes.length == 0 ? "" : modes[0], ValueType.ENUM);
        this.modes = modes;
        this.metadata("options", Arrays.asList(modes));
        this.alias(displayName);
    }

    public String[] getModes() {
        return this.modes;
    }

    public ModeValue withDefault(String value) {
        this.setValue(value);
        return this;
    }

    public ModeValue withVisibility(ValueCondition visibility) {
        this.visibleWhen(visibility);
        return this;
    }

    public boolean is(String value) {
        return value != null && value.equalsIgnoreCase(String.valueOf(this.getValue()));
    }
}
