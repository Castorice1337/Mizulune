package shit.zen.value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ValueGroup extends Value<List<Value<?>>> {
    private final List<Value<?>> children = new ArrayList<>();

    public ValueGroup(String id, String displayName) {
        this(id, displayName, "");
    }

    public ValueGroup(String id, String displayName, String description) {
        super(id, displayName, description, List.of(), ValueType.GROUP);
    }

    public List<Value<?>> getChildren() {
        return this.children;
    }

    public List<Value<?>> getVisibleChildren() {
        return this.getChildren().stream().filter(Value::isVisible).toList();
    }

    public boolean areChildrenVisible() {
        return this.isVisible();
    }

    public void clearChildren() {
        this.children.clear();
    }

    public <T extends Value<?>> T add(T value) {
        value.setParent(this);
        this.children.add(value);
        return value;
    }

    public ValueGroup group(String id, String displayName) {
        return this.add(new ValueGroup(id, displayName));
    }

    public ToggleValueGroup toggleGroup(String id, String displayName, boolean enabled) {
        return this.add(new ToggleValueGroup(id, displayName, enabled));
    }

    public ModeValueGroup modeGroup(String id, String displayName, String defaultMode, String... modes) {
        ModeValueGroup group = this.add(new ModeValueGroup(id, displayName, defaultMode));
        Arrays.stream(modes).forEach(mode -> group.mode(mode, mode));
        return group;
    }

    public Value<Boolean> bool(String id, String displayName, boolean defaultValue) {
        return this.add(new Value<>(id, displayName, "", defaultValue, ValueType.BOOLEAN));
    }

    public Value<Number> integer(String id, String displayName, int defaultValue, int min, int max, int step) {
        return this.add(new Value<Number>(id, displayName, "", defaultValue, ValueType.INTEGER)
                .range(min, max, step));
    }

    public Value<Number> decimal(String id, String displayName, double defaultValue, double min, double max, double step) {
        return this.add(new Value<Number>(id, displayName, "", defaultValue, ValueType.DECIMAL)
                .range(min, max, step));
    }

    public Value<String> text(String id, String displayName, String defaultValue) {
        return this.add(new Value<>(id, displayName, "", defaultValue, ValueType.TEXT));
    }

    public Value<String> enumChoice(String id, String displayName, String defaultValue, String... options) {
        return this.add(new Value<String>(id, displayName, "", defaultValue, ValueType.ENUM)
                .metadata("options", Arrays.asList(options)));
    }

    public Value<List<String>> multiEnum(String id, String displayName, Collection<String> defaults, String... options) {
        return this.add(new Value<List<String>>(id, displayName, "", new ArrayList<>(defaults), ValueType.MULTI_ENUM)
                .metadata("options", Arrays.asList(options)));
    }

    public Value<MizuColor> color(String id, String displayName, MizuColor defaultValue) {
        return this.add(new Value<>(id, displayName, "", defaultValue, ValueType.COLOR));
    }

    public Value<GradientSpec> gradient(String id, String displayName, MizuColor start, MizuColor end) {
        return this.add(new Value<>(id, displayName, "", new GradientSpec(start, end), ValueType.GRADIENT));
    }

    public Value<NumericRange> intRange(String id, String displayName, int lower, int upper, int min, int max, int step) {
        return this.add(new Value<>(id, displayName, "", new NumericRange(lower, upper, min, max, step, true), ValueType.INT_RANGE));
    }

    public Value<NumericRange> decimalRange(String id, String displayName, double lower, double upper, double min, double max, double step) {
        return this.add(new Value<>(id, displayName, "", new NumericRange(lower, upper, min, max, step, false), ValueType.DECIMAL_RANGE));
    }

    public Value<String> resourceLocation(String id, String displayName, String defaultValue) {
        return this.add(new Value<>(id, displayName, "", defaultValue, ValueType.RESOURCE_LOCATION));
    }

    public Value<String> registry(String id, String displayName, String defaultValue, ValueType type) {
        return this.add(new Value<String>(id, displayName, "", defaultValue, type));
    }

    public Optional<Value<?>> findByPath(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String normalized = path.startsWith(this.getPath() + ".") ? path.substring(this.getPath().length() + 1) : path;
        if (normalized.equals(this.getId()) || normalized.equals(this.getPath())) {
            return Optional.of(this);
        }
        String[] parts = normalized.split("\\.");
        return this.findByParts(parts, 0);
    }

    protected Optional<Value<?>> findByParts(String[] parts, int index) {
        if (index >= parts.length) {
            return Optional.of(this);
        }
        for (Value<?> child : this.getChildren()) {
            if (!child.getId().equals(Value.normalizeId(parts[index]))) {
                continue;
            }
            if (index == parts.length - 1) {
                return Optional.of(child);
            }
            if (child instanceof ValueGroup group) {
                return group.findByParts(parts, index + 1);
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    public Optional<Value<?>> findByAliasOrDisplayName(String key) {
        if (this.matchesKey(key)) {
            return Optional.of(this);
        }
        for (Value<?> child : this.getChildren()) {
            if (child.matchesKey(key)) {
                return Optional.of(child);
            }
            if (child instanceof ValueGroup group) {
                Optional<Value<?>> nested = group.findByAliasOrDisplayName(key);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return Optional.empty();
    }

    public List<Value<?>> flatten() {
        List<Value<?>> flattened = new ArrayList<>();
        for (Value<?> child : this.getChildren()) {
            flattened.add(child);
            if (child instanceof ValueGroup group) {
                flattened.addAll(group.flatten());
            }
        }
        return flattened;
    }

    public Map<String, Value<?>> childMap() {
        Map<String, Value<?>> map = new LinkedHashMap<>();
        for (Value<?> child : this.getChildren()) {
            map.put(child.getId(), child);
        }
        return map;
    }
}
