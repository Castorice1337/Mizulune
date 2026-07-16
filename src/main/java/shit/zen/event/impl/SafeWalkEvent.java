package shit.zen.event.impl;

import shit.zen.event.EventMarker;

public final class SafeWalkEvent implements EventMarker {
    private boolean safeWalk;
    private boolean modified;

    public SafeWalkEvent(boolean safeWalk) {
        this.safeWalk = safeWalk;
    }

    public boolean isSafeWalk() {
        return this.safeWalk;
    }

    public void setSafeWalk(boolean safeWalk) {
        this.safeWalk = safeWalk;
        this.modified = true;
    }

    public boolean isModified() {
        return this.modified;
    }
}
