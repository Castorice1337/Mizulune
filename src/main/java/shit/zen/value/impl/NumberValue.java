package shit.zen.value.impl;

import shit.zen.value.Value;
import shit.zen.value.ValueCondition;
import shit.zen.value.ValueType;

public class NumberValue extends Value<Number> {
    private final Number min;
    private final Number max;
    private final Number step;

    public NumberValue(String displayName, Number defaultValue, Number min, Number max, Number step) {
        super(Value.normalizeId(displayName), displayName, "", defaultValue,
                isIntegerValue(defaultValue, min, max, step) ? ValueType.INTEGER : ValueType.DECIMAL);
        this.min = min;
        this.max = max;
        this.step = step;
        this.range(min, max, step);
        this.alias(displayName);
    }

    public NumberValue(String displayName, Number defaultValue, Number min, Number max, Number step, ValueCondition visibility) {
        this(displayName, defaultValue, min, max, step);
        this.visibleWhen(visibility);
    }

    public Number getMin() {
        return this.min;
    }

    public Number getMax() {
        return this.max;
    }

    public Number getStep() {
        return this.step;
    }

    public NumberValue withVisibility(ValueCondition visibility) {
        this.visibleWhen(visibility);
        return this;
    }

    private static boolean isIntegerValue(Number... values) {
        for (Number value : values) {
            if (value == null) {
                continue;
            }
            double number = value.doubleValue();
            if (Math.abs(number - Math.rint(number)) > 0.000001) {
                return false;
            }
        }
        return true;
    }
}
