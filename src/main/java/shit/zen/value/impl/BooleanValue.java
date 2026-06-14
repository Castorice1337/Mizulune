package shit.zen.value.impl;

import shit.zen.value.Value;
import shit.zen.value.ValueCondition;
import shit.zen.value.ValueType;

public class BooleanValue extends Value<Boolean> {
    public BooleanValue(String displayName, Boolean defaultValue) {
        super(Value.normalizeId(displayName), displayName, "", defaultValue, ValueType.BOOLEAN);
        this.alias(displayName);
    }

    public BooleanValue(String displayName, Boolean defaultValue, ValueCondition visibility) {
        this(displayName, defaultValue);
        this.visibleWhen(visibility);
    }

    public BooleanValue withVisibility(ValueCondition visibility) {
        this.visibleWhen(visibility);
        return this;
    }
}
