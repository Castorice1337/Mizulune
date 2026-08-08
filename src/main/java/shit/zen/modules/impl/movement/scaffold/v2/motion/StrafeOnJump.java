/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldJumpStrafe:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as a side-effect-free Scaffold v2 motion policy.
 */
package shit.zen.modules.impl.movement.scaffold.v2.motion;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import net.minecraft.world.phys.Vec3;
import shit.zen.utils.game.DirectionalInput;

public final class StrafeOnJump {
    public static final float MIN_SPEED = 0.1f;
    public static final float MAX_SPEED = 1.0f;
    public static final SpeedRange DEFAULT_STRAIGHT_SPEED = new SpeedRange(0.48f, 0.49f);
    public static final SpeedRange DEFAULT_DIAGONAL_SPEED = new SpeedRange(0.48f, 0.49f);
    public static final Settings DEFAULTS = new Settings(
            false,
            DEFAULT_STRAIGHT_SPEED,
            DEFAULT_DIAGONAL_SPEED);

    private StrafeOnJump() {
    }

    public static Decision apply(AfterJumpInput input, Settings settings) {
        return apply(input, settings, ThreadLocalRandom.current());
    }

    public static Decision apply(
            AfterJumpInput input,
            Settings settings,
            RandomGenerator random) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(random, "random");

        if (!settings.enabled()) {
            return new Decision(input.velocity(), false, 0.0f, 0.0f, false);
        }

        float direction = Strafe.movementYaw(input.playerYaw(), input.directionalInput()) + 180.0f;
        float directionSteps = direction / 45.0f;
        float movingYaw = (float) Math.rint(directionSteps) * 45.0f;
        boolean movingStraight = movingYaw % 90.0f == 0.0f;
        SpeedRange range = movingStraight ? settings.straightSpeed() : settings.diagonalSpeed();
        float speed = range.sample(random);
        Vec3 velocity = Strafe.withStrafe(
                input.velocity(),
                speed,
                input.directionalInput(),
                input.playerYaw());
        return new Decision(velocity, true, speed, movingYaw, movingStraight);
    }

    public record Settings(boolean enabled, SpeedRange straightSpeed, SpeedRange diagonalSpeed) {
        public Settings {
            Objects.requireNonNull(straightSpeed, "straightSpeed");
            Objects.requireNonNull(diagonalSpeed, "diagonalSpeed");
        }
    }

    public record SpeedRange(float minimum, float maximum) {
        public SpeedRange {
            if (!Float.isFinite(minimum)
                    || !Float.isFinite(maximum)
                    || minimum < MIN_SPEED
                    || maximum > MAX_SPEED
                    || minimum > maximum) {
                throw new IllegalArgumentException("speed range must be ordered within 0.1..1.0");
            }
        }

        public float sample(RandomGenerator random) {
            Objects.requireNonNull(random, "random");
            if (this.minimum >= this.maximum) {
                return this.minimum;
            }
            return this.minimum + random.nextFloat() * (this.maximum - this.minimum);
        }
    }

    public record AfterJumpInput(Vec3 velocity, DirectionalInput directionalInput, float playerYaw) {
        public AfterJumpInput {
            Objects.requireNonNull(velocity, "velocity");
            Objects.requireNonNull(directionalInput, "directionalInput");
        }
    }

    public record Decision(
            Vec3 velocity,
            boolean writesVelocity,
            float sampledSpeed,
            float movingYaw,
            boolean movingStraight) {
        public Decision {
            Objects.requireNonNull(velocity, "velocity");
        }
    }
}
