package shit.zen.modules.impl.movement.scaffold.v2;

import shit.zen.utils.rotation.Rotation;

/** Keeps Scaffold's last server-facing rotation before normal reset takes over. */
public final class ScaffoldRotationRetention {
    private Rotation retainedRotation;
    private int ticksRemaining;
    private boolean hadTarget;

    public void onTarget(Rotation rotation, int ticksUntilReset) {
        if (rotation == null) {
            this.onMissingTarget(null);
            return;
        }
        this.retainedRotation = rotation.clone();
        this.ticksRemaining = Math.max(0, ticksUntilReset);
        this.hadTarget = true;
    }

    public void onMissingTarget(Rotation lastAppliedRotation) {
        if (this.hadTarget && lastAppliedRotation != null) {
            this.retainedRotation = lastAppliedRotation.clone();
        }
        this.hadTarget = false;
        if (this.retainedRotation == null) {
            return;
        }
        if (this.ticksRemaining <= 0) {
            this.clear();
            return;
        }
        this.ticksRemaining--;
    }

    public Rotation rotation() {
        return this.retainedRotation == null ? null : this.retainedRotation.clone();
    }

    public boolean active() {
        return this.retainedRotation != null;
    }

    public int ticksRemaining() {
        return this.ticksRemaining;
    }

    public void clear() {
        this.retainedRotation = null;
        this.ticksRemaining = 0;
        this.hadTarget = false;
    }
}
