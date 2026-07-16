/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldTowerKarhu.
 * Licensed under GNU GPL v3 or later.
 */
package shit.zen.modules.impl.movement.scaffold.v2.tower;

import net.minecraft.world.phys.Vec3;

public final class KarhuTower implements Tower {
    private final Settings settings;
    private Phase phase = Phase.IDLE;

    public KarhuTower() {
        this(Settings.DEFAULT);
    }

    public KarhuTower(Settings settings) {
        this.settings = settings == null ? Settings.DEFAULT : settings;
    }

    @Override
    public String name() {
        return "Karhu";
    }

    public Settings settings() {
        return this.settings;
    }

    public Phase phase() {
        return this.phase;
    }

    @Override
    public void onJump(JumpInput input) {
        if (input.isValid()) {
            this.phase = Phase.WAITING_FOR_AIR;
        }
    }

    @Override
    public TickMotionDecision tick(TickInput input) {
        if (this.phase == Phase.IDLE) {
            return TickMotionDecision.idle(input.velocity(), false);
        }

        Float timerSpeed = null;
        if (this.phase == Phase.WAITING_FOR_AIR) {
            if (input.onGround()) {
                return TickMotionDecision.idle(input.velocity(), true);
            }
            timerSpeed = (float) this.settings.timerSpeed();
            if (!this.settings.pulldown()) {
                this.phase = Phase.IDLE;
                return new TickMotionDecision(
                        true,
                        input.velocity(),
                        false,
                        null,
                        timerSpeed,
                        false,
                        Vec3.ZERO);
            }
            this.phase = Phase.WAITING_FOR_PULLDOWN;
        }

        if (!input.onGround() && input.velocity().y < this.settings.triggerMotion()) {
            this.phase = Phase.IDLE;
            if (input.blockBelow()) {
                Vec3 velocity = new Vec3(
                        input.velocity().x,
                        input.velocity().y - 1.0,
                        input.velocity().z);
                return new TickMotionDecision(
                        true,
                        velocity,
                        true,
                        null,
                        timerSpeed,
                        false,
                        Vec3.ZERO);
            }
        }

        return new TickMotionDecision(
                true,
                input.velocity(),
                false,
                null,
                timerSpeed,
                false,
                Vec3.ZERO);
    }

    @Override
    public void reset() {
        this.phase = Phase.IDLE;
    }

    public enum Phase {
        IDLE,
        WAITING_FOR_AIR,
        WAITING_FOR_PULLDOWN
    }

    public record Settings(double timerSpeed, double triggerMotion, boolean pulldown) {
        public static final Settings DEFAULT = new Settings(5.0, 0.06, true);

        public Settings {
            if (timerSpeed < 0.1 || timerSpeed > 10.0) {
                throw new IllegalArgumentException("timerSpeed must be in [0.1, 10]");
            }
            if (triggerMotion < 0.0 || triggerMotion > 0.2) {
                throw new IllegalArgumentException("triggerMotion must be in [0, 0.2]");
            }
        }
    }
}
