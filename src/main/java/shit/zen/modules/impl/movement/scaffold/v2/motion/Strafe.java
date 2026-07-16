/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldStrafeFeature and EntityExtensions.withStrafe:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as a side-effect-free Scaffold v2 motion policy.
 */
package shit.zen.modules.impl.movement.scaffold.v2.motion;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;
import shit.zen.utils.game.DirectionalInput;

public final class Strafe {
    public static final float MIN_SPEED = 0.0f;
    public static final float MAX_SPEED = 5.0f;
    public static final double HYPIXEL_BASE_SPEED = 0.207;
    public static final double HYPIXEL_SPEED_EFFECT_SPEED = 0.295;
    public static final double HYPIXEL_LOW_SPEED = 0.09800000190734863;
    public static final Settings DEFAULTS = new Settings(false, 0.247f, false, false);

    private int moveTicks;

    public void onEnabled() {
        this.reset();
    }

    public void reset() {
        this.moveTicks = 0;
    }

    public Decision tick(TickInput input, Settings settings) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(settings, "settings");

        if (!settings.enabled()) {
            return Decision.unchanged(input.velocity(), this.moveTicks);
        }

        this.moveTicks = input.moving() ? this.moveTicks + 1 : 0;
        if (settings.onlyOnGround() && !input.onGround()) {
            return Decision.unchanged(input.velocity(), this.moveTicks);
        }

        if (!settings.hypixel()) {
            /*
             * The current liquidSRC non-Hypixel branch calls withStrafe but does not assign
             * its returned Vec3 back to player.deltaMovement. Keep that exact no-write
             * behavior instead of silently fixing the upstream omission.
             */
            withStrafe(input.velocity(), settings.speed(), input.directionalInput(), input.playerYaw());
            return new Decision(input.velocity(), false, settings.speed(), this.moveTicks);
        }

        double speed = input.speedEffectAmplifier() >= 0
                ? HYPIXEL_SPEED_EFFECT_SPEED
                : HYPIXEL_BASE_SPEED;
        if (input.playerTickCount() % 20 == 0 || this.moveTicks <= 7) {
            speed = HYPIXEL_LOW_SPEED;
        }

        Vec3 velocity = withStrafe(
                input.velocity(),
                speed,
                input.directionalInput(),
                input.playerYaw());
        return new Decision(velocity, true, speed, this.moveTicks);
    }

    public Decision onDisabled(Vec3 velocity, Settings settings) {
        Objects.requireNonNull(velocity, "velocity");
        Objects.requireNonNull(settings, "settings");

        if (!settings.hypixel()) {
            return Decision.unchanged(velocity, this.moveTicks);
        }

        return new Decision(
                new Vec3(velocity.x * 0.5, velocity.y, velocity.z * 0.5),
                true,
                0.5,
                this.moveTicks);
    }

    public int moveTicks() {
        return this.moveTicks;
    }

    static Vec3 withStrafe(
            Vec3 velocity,
            double speed,
            DirectionalInput input,
            float facingYaw) {
        Objects.requireNonNull(velocity, "velocity");
        Objects.requireNonNull(input, "input");

        if (!input.isMoving()) {
            return new Vec3(0.0, velocity.y, 0.0);
        }

        double angle = Math.toRadians(movementYaw(facingYaw, input));
        return new Vec3(-Math.sin(angle) * speed, velocity.y, Math.cos(angle) * speed);
    }

    static float movementYaw(float facingYaw, DirectionalInput input) {
        Objects.requireNonNull(input, "input");

        float actualYaw = facingYaw;
        float forwardMultiplier;
        if (input.backwards() && !input.forwards()) {
            actualYaw += 180.0f;
            forwardMultiplier = -0.5f;
        } else if (input.forwards() && !input.backwards()) {
            forwardMultiplier = 0.5f;
        } else {
            forwardMultiplier = 1.0f;
        }

        if (input.left() && !input.right()) {
            actualYaw -= 90.0f * forwardMultiplier;
        }
        if (input.right() && !input.left()) {
            actualYaw += 90.0f * forwardMultiplier;
        }
        return actualYaw;
    }

    public record Settings(boolean enabled, float speed, boolean hypixel, boolean onlyOnGround) {
        public Settings {
            if (!Float.isFinite(speed) || speed < MIN_SPEED || speed > MAX_SPEED) {
                throw new IllegalArgumentException("speed must be in 0.0..5.0");
            }
        }
    }

    public record TickInput(
            Vec3 velocity,
            DirectionalInput directionalInput,
            float playerYaw,
            boolean moving,
            boolean onGround,
            int playerTickCount,
            int speedEffectAmplifier) {
        public TickInput {
            Objects.requireNonNull(velocity, "velocity");
            Objects.requireNonNull(directionalInput, "directionalInput");
        }
    }

    public record Decision(Vec3 velocity, boolean writesVelocity, double requestedSpeed, int moveTicks) {
        public Decision {
            Objects.requireNonNull(velocity, "velocity");
        }

        static Decision unchanged(Vec3 velocity, int moveTicks) {
            return new Decision(velocity, false, 0.0, moveTicks);
        }
    }
}
