package shit.zen.event.impl;

import shit.zen.event.EventMarker;

/**
 * Fired after {@code RotationHandler} has resolved the active owner and
 * advanced its rotation for the current client tick.
 */
public final class RotationResolvedEvent implements EventMarker {
    private final int tick;

    public RotationResolvedEvent(int tick) {
        this.tick = tick;
    }

    public int getTick() {
        return this.tick;
    }
}
