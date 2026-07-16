/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldAccelerationFeature:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as a side-effect-free Scaffold v2 motion policy.
 */
package shit.zen.modules.impl.movement.scaffold.v2.motion;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

public final class Acceleration {
    public static final float MIN_SPEED_MULTIPLIER = 0.1f;
    public static final float MAX_SPEED_MULTIPLIER = 3.0f;
    public static final Settings DEFAULTS = new Settings(false, 0.6f, false);

    private Acceleration() {
    }

    public static Vec3 apply(Vec3 velocity, boolean onGround, Settings settings) {
        Objects.requireNonNull(velocity, "velocity");
        Objects.requireNonNull(settings, "settings");

        if (!settings.enabled() || settings.onlyOnGround() && !onGround) {
            return velocity;
        }

        double multiplier = settings.speedMultiplier();
        return new Vec3(velocity.x * multiplier, velocity.y, velocity.z * multiplier);
    }

    public record Settings(boolean enabled, float speedMultiplier, boolean onlyOnGround) {
        public Settings {
            if (!Float.isFinite(speedMultiplier)
                    || speedMultiplier < MIN_SPEED_MULTIPLIER
                    || speedMultiplier > MAX_SPEED_MULTIPLIER) {
                throw new IllegalArgumentException("speedMultiplier must be in 0.1..3.0");
            }
        }
    }
}
