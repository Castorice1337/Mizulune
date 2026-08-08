/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldTowerMotion.
 * Licensed under GNU GPL v3 or later.
 */
package shit.zen.modules.impl.movement.scaffold.v2.tower;

import net.minecraft.world.phys.Vec3;

public final class MotionTower implements Tower {
    private final Settings settings;
    private double jumpOffPosition = Double.NaN;

    public MotionTower() {
        this(Settings.DEFAULT);
    }

    public MotionTower(Settings settings) {
        this.settings = settings == null ? Settings.DEFAULT : settings;
    }

    @Override
    public String name() {
        return "Motion";
    }

    public Settings settings() {
        return this.settings;
    }

    public double jumpOffPosition() {
        return this.jumpOffPosition;
    }

    @Override
    public void onJump(JumpInput input) {
        this.jumpOffPosition = input.playerY();
    }

    @Override
    public TickMotionDecision tick(TickInput input) {
        if (!input.isTowerActive()) {
            this.jumpOffPosition = Double.NaN;
            return TickMotionDecision.idle(input.velocity(), false);
        }
        if (Double.isNaN(this.jumpOffPosition)
                || input.playerY() <= this.jumpOffPosition + this.settings.triggerHeight()) {
            return TickMotionDecision.idle(input.velocity(), true);
        }

        double snappedY = TowerMath.truncate(input.playerY());
        Vec3 velocity = new Vec3(
                input.velocity().x * this.settings.slow(),
                this.settings.motion(),
                input.velocity().z * this.settings.slow());
        this.jumpOffPosition = snappedY;
        return new TickMotionDecision(
                true,
                velocity,
                true,
                snappedY,
                null,
                true,
                Vec3.ZERO);
    }

    @Override
    public void reset() {
        this.jumpOffPosition = Double.NaN;
    }

    public record Settings(double motion, double triggerHeight, double slow) {
        public static final Settings DEFAULT = new Settings(0.42, 0.78, 1.0);

        public Settings {
            if (motion < 0.0 || motion > 1.0) {
                throw new IllegalArgumentException("motion must be in [0, 1]");
            }
            if (triggerHeight < 0.76 || triggerHeight > 1.0) {
                throw new IllegalArgumentException("triggerHeight must be in [0.76, 1]");
            }
            if (slow < 0.0 || slow > 3.0) {
                throw new IllegalArgumentException("slow must be in [0, 3]");
            }
        }
    }
}
