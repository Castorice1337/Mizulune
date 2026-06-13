package shit.zen.value;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModeValueGroup extends ValueGroup {
    private final Value<String> activeValue;
    private final String defaultMode;
    private final Map<String, ValueGroup> modes = new LinkedHashMap<>();

    public ModeValueGroup(String id, String displayName, String defaultMode) {
        super(id, displayName);
        this.defaultMode = Value.normalizeId(defaultMode);
        this.activeValue = new Value<>("mode", "Mode", "", this.defaultMode, ValueType.ENUM);
        this.activeValue.setParent(this);
    }

    public ValueGroup mode(String id, String displayName) {
        String normalized = Value.normalizeId(id);
        ValueGroup group = new ValueGroup(normalized, displayName);
        group.setParent(this);
        this.modes.put(normalized, group);
        this.activeValue.metadata("options", this.modes.keySet().stream().toList());
        if (this.modes.containsKey(this.defaultMode)) {
            this.activeValue.setValue(this.defaultMode);
        } else if (!this.modes.containsKey(this.activeValue.getValue())) {
            this.activeValue.setValue(normalized);
        }
        return group;
    }

    public Value<String> getActiveValue() {
        return this.activeValue;
    }

    public String getActiveModeId() {
        return Value.normalizeId(this.activeValue.getValue());
    }

    public Map<String, ValueGroup> getModes() {
        return this.modes;
    }

    public ValueGroup getActiveGroup() {
        ValueGroup group = this.modes.get(this.getActiveModeId());
        if (group != null) {
            return group;
        }
        return this.modes.values().stream().findFirst().orElse(null);
    }

    @Override
    public List<Value<?>> getChildren() {
        List<Value<?>> children = new ArrayList<>();
        children.addAll(this.modes.values());
        return children;
    }

    @Override
    public List<Value<?>> getVisibleChildren() {
        ValueGroup activeGroup = this.getActiveGroup();
        return activeGroup == null || !this.areChildrenVisible() ? List.of() : List.of(activeGroup);
    }

    @Override
    public ValueType getType() {
        return ValueType.MODE_GROUP;
    }
}
