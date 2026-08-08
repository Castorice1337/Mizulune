package shit.zen.value;

import java.util.Objects;

/** A non-persistent ValueTree leaf rendered as a push button. */
public final class ActionValue extends Value<Boolean> {
    private final String actionLabel;
    private final Runnable action;

    public ActionValue(String id, String displayName, String actionLabel, Runnable action) {
        super(id, displayName, "", false, ValueType.BOOLEAN);
        this.actionLabel = actionLabel == null || actionLabel.isBlank() ? "Run" : actionLabel;
        this.action = Objects.requireNonNull(action, "action");
    }

    public String getActionLabel() {
        return this.actionLabel;
    }

    public void trigger() {
        this.action.run();
    }
}
