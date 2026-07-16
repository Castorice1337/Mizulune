package shit.zen.event.impl;

import shit.zen.event.EventMarker;

public class StrafeEvent implements EventMarker {
    private float forward;
    private float strafe;
    private boolean jumping;
    private boolean sneaking;

    public StrafeEvent(float forward, float strafe, boolean jumping) {
        this(forward, strafe, jumping, false);
    }

    public StrafeEvent(float forward, float strafe, boolean jumping, boolean sneaking) {
        this.forward = forward;
        this.strafe = strafe;
        this.jumping = jumping;
        this.sneaking = sneaking;
    }

    public float getForward() {
        return this.forward;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    public float getStrafe() {
        return this.strafe;
    }

    public void setStrafe(float strafe) {
        this.strafe = strafe;
    }

    public boolean isJumping() {
        return this.jumping;
    }

    public void setJumping(boolean jumping) {
        this.jumping = jumping;
    }

    public boolean isSneaking() {
        return this.sneaking;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }

    /**
     * Compatibility alias for the old, incorrectly named jump field.
     */
    @Deprecated
    public boolean isSprinting() {
        return this.isJumping();
    }

    /**
     * Compatibility alias for the old, incorrectly named jump field.
     */
    @Deprecated
    public void setSprinting(boolean jumping) {
        this.setJumping(jumping);
    }
}
