/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldTowerPulldown.
 * Licensed under GNU GPL v3 or later.
 */
package shit.zen.modules.impl.movement.scaffold.v2.tower;

import net.minecraft.world.phys.Vec3;

public final class PulldownTower implements Tower {
    private final Settings settings;
    private boolean armed;

    public PulldownTower() {
        this(Settings.DEFAULT);
    }

    public PulldownTower(Settings settings) {
        this.settings = settings == null ? Settings.DEFAULT : settings;
    }

    @Override
    public String name() {
        return "Pulldown";
    }

    public Settings settings() {
        return this.settings;
    }

    public boolean isArmed() {
        return this.armed;
    }

    @Override
    public void onJump(JumpInput input) {
        if (input.isValid()) {
            this.armed = true;
        }
    }

    @Override
    public TickMotionDecision tick(TickInput input) {
        if (!this.armed) {
            return TickMotionDecision.idle(input.velocity(), false);
        }
        if (input.onGround() || input.velocity().y >= this.settings.triggerMotion()) {
            return TickMotionDecision.idle(input.velocity(), true);
        }

        this.armed = false;
        if (!input.blockBelow()) {
            return TickMotionDecision.idle(input.velocity(), true);
        }
        Vec3 velocity = new Vec3(input.velocity().x, -1.0, input.velocity().z);
        return new TickMotionDecision(
                true,
                velocity,
                true,
                null,
                null,
                false,
                Vec3.ZERO);
    }

    @Override
    public void reset() {
        this.armed = false;
    }

    public record Settings(double triggerMotion) {
        public static final Settings DEFAULT = new Settings(0.1);

        public Settings {
            if (triggerMotion < 0.0 || triggerMotion > 0.2) {
                throw new IllegalArgumentException("triggerMotion must be in [0, 0.2]");
            }
        }
    }
}
