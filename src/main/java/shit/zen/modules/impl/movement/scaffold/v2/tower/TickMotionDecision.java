/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce Scaffold tower mode effects.
 * Licensed under GNU GPL v3 or later.
 */
package shit.zen.modules.impl.movement.scaffold.v2.tower;

import net.minecraft.world.phys.Vec3;

public record TickMotionDecision(
        boolean active,
        Vec3 velocity,
        boolean velocityChanged,
        Double snappedY,
        Float timerSpeed,
        boolean awardJumpStat,
        Vec3 outgoingMoveOffset) {
    public TickMotionDecision {
        velocity = velocity == null ? Vec3.ZERO : velocity;
        outgoingMoveOffset = outgoingMoveOffset == null ? Vec3.ZERO : outgoingMoveOffset;
    }

    public static TickMotionDecision idle(Vec3 velocity, boolean active) {
        return new TickMotionDecision(
                active,
                velocity,
                false,
                null,
                null,
                false,
                Vec3.ZERO);
    }

    public boolean hasPositionSnap() {
        return this.snappedY != null;
    }

    public boolean hasTimerRequest() {
        return this.timerSpeed != null;
    }

    public boolean hasOutgoingMoveOffset() {
        return !this.outgoingMoveOffset.equals(Vec3.ZERO);
    }
}
