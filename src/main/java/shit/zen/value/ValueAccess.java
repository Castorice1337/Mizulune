package shit.zen.value;

import java.util.List;

public final class ValueAccess {
    private ValueAccess() {
    }

    public static boolean bool(Value<Boolean> value) {
        return value != null && Boolean.TRUE.equals(value.getValue());
    }

    public static int numberInt(Value<Number> value) {
        return value == null || value.getValue() == null ? 0 : value.getValue().intValue();
    }

    public static float numberFloat(Value<Number> value) {
        return value == null || value.getValue() == null ? 0.0f : value.getValue().floatValue();
    }

    public static double numberDouble(Value<Number> value) {
        return value == null || value.getValue() == null ? 0.0 : value.getValue().doubleValue();
    }

    public static boolean modeIs(Value<String> value, String expected) {
        return value != null && expected != null && expected.equalsIgnoreCase(String.valueOf(value.getValue()));
    }

    public static boolean selected(Value<List<String>> value, String option) {
        return value != null && value.getValue() != null && value.getValue().contains(option);
    }
}
