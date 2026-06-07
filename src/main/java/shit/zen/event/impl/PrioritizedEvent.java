package shit.zen.event.impl;

import shit.zen.event.EventMarker;
import shit.zen.event.Prioritized;

public abstract class PrioritizedEvent
implements Prioritized,
EventMarker {
    private final byte priority;

    protected PrioritizedEvent(byte by) {
        this.priority = by;
    }

    @Override
    public byte getPriority() {
        return this.priority;
    }
}