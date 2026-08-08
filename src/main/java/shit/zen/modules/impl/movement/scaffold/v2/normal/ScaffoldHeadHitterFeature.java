/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldHeadHitterFeature:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as a side-effect-free Java movement decision.
 */
package shit.zen.modules.impl.movement.scaffold.v2.normal;

import java.util.Objects;

public final class ScaffoldHeadHitterFeature {
    public static final Settings DEFAULTS = new Settings(false);

    private ScaffoldHeadHitterFeature() {
    }

    public static Decision decide(Settings settings, Frame frame) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(frame, "frame");
        boolean canHeadHit = !frame.blockTwoAboveAir() && frame.onGround();
        boolean active = settings.enabled() && canHeadHit;
        MotionAction action = active && frame.moving()
                ? MotionAction.JUMP_FROM_GROUND
                : MotionAction.NONE;
        return new Decision(canHeadHit, active, action);
    }

    public enum MotionAction {
        NONE,
        JUMP_FROM_GROUND
    }

    public record Settings(boolean enabled) {
    }

    public record Frame(
            boolean blockTwoAboveAir,
            boolean onGround,
            boolean moving) {
    }

    public record Decision(
            boolean canHeadHit,
            boolean active,
            MotionAction action) {
        public Decision {
            Objects.requireNonNull(action, "action");
        }

        public boolean shouldJumpFromGround() {
            return this.action == MotionAction.JUMP_FROM_GROUND;
        }
    }
}
