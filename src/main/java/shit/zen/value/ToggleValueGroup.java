package shit.zen.value;

public class ToggleValueGroup extends ValueGroup {
    private final Value<Boolean> enabledValue;

    public ToggleValueGroup(String id, String displayName, boolean enabled) {
        super(id, displayName);
        this.enabledValue = new Value<>("enabled", displayName, "", enabled, ValueType.BOOLEAN);
        this.enabledValue.setParent(this);
    }

    public Value<Boolean> getEnabledValue() {
        return this.enabledValue;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(this.enabledValue.getValue());
    }

    public void setEnabled(boolean enabled) {
        this.enabledValue.setValue(enabled);
    }

    @Override
    public ValueType getType() {
        return ValueType.TOGGLE_GROUP;
    }

    @Override
    public boolean areChildrenVisible() {
        return super.areChildrenVisible() && this.isEnabled();
    }
}
