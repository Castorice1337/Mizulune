package shit.zen.value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class Value<T> {
    private final String id;
    private final String displayName;
    private final String description;
    private final T defaultValue;
    private final ValueType type;
    private final Map<String, Object> metadata = new LinkedHashMap<>();
    private final List<String> aliases = new ArrayList<>();
    private final List<ValueListener<T>> listeners = new ArrayList<>();
    private ValueCondition visibility = ValueCondition.ALWAYS;
    private ValueGroup parent;
    private T value;

    public Value(String id, String displayName, String description, T defaultValue, ValueType type) {
        this.id = normalizeId(id);
        this.displayName = displayName == null || displayName.isBlank() ? id : displayName;
        this.description = description == null ? "" : description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.type = type;
    }

    public String getId() {
        return this.id;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getDescription() {
        return this.description;
    }

    public T getDefaultValue() {
        return this.defaultValue;
    }

    public T getValue() {
        return this.value;
    }

    public void setValue(T value) {
        T oldValue = this.getValue();
        if (Objects.equals(oldValue, value)) {
            return;
        }
        this.value = value;
        for (ValueListener<T> listener : this.listeners) {
            listener.onChanged(this, oldValue, value);
        }
    }

    @SuppressWarnings("unchecked")
    public void setRawValue(Object value) {
        this.setValue((T)value);
    }

    public ValueType getType() {
        return this.type;
    }

    public Map<String, Object> getMetadata() {
        return this.metadata;
    }

    public Value<T> metadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    public Value<T> range(Number min, Number max, Number step) {
        return this.metadata("min", min).metadata("max", max).metadata("step", step);
    }

    public List<String> getAliases() {
        return this.aliases;
    }

    public Value<T> alias(String alias) {
        if (alias != null && !alias.isBlank()) {
            this.aliases.add(alias);
        }
        return this;
    }

    public Value<T> visibleWhen(ValueCondition visibility) {
        this.visibility = visibility == null ? ValueCondition.ALWAYS : visibility;
        return this;
    }

    public ValueCondition getVisibility() {
        return this.visibility;
    }

    public boolean isVisible() {
        if (!this.visibility.visible()) {
            return false;
        }
        return this.parent == null || this.parent.areChildrenVisible();
    }

    public Value<T> listener(ValueListener<T> listener) {
        if (listener != null) {
            this.listeners.add(listener);
        }
        return this;
    }

    public void reset() {
        this.setValue(this.defaultValue);
    }

    public ValueGroup getParent() {
        return this.parent;
    }

    void setParent(ValueGroup parent) {
        this.parent = parent;
    }

    public String getPath() {
        if (this.parent == null) {
            return this.id;
        }
        return this.parent.getPath() + "." + this.id;
    }

    public String getLocalPath(ValueGroup root) {
        String path = this.getPath();
        String rootPath = root.getPath();
        if (path.equals(rootPath)) {
            return "";
        }
        if (path.startsWith(rootPath + ".")) {
            return path.substring(rootPath.length() + 1);
        }
        return path;
    }

    public boolean matchesKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = normalizeId(key);
        if (this.id.equals(normalized) || this.getPath().equals(key)) {
            return true;
        }
        if (this.displayName.equalsIgnoreCase(key) || normalizeId(this.displayName).equals(normalized)) {
            return true;
        }
        for (String alias : this.aliases) {
            if (alias.equalsIgnoreCase(key) || normalizeId(alias).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "value";
        }
        StringBuilder builder = new StringBuilder();
        String value = raw.trim();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isUpperCase(ch) && i > 0 && builder.length() > 0 && builder.charAt(builder.length() - 1) != '_') {
                builder.append('_');
            }
            if (Character.isLetterOrDigit(ch)) {
                builder.append(Character.toLowerCase(ch));
            } else if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '_') {
                builder.append('_');
            }
        }
        String id = builder.toString().replaceAll("_+", "_");
        id = id.replaceAll("^_|_$", "");
        return id.isEmpty() ? "value" : id.toLowerCase(Locale.ROOT);
    }
}
