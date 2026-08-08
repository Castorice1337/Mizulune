/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldSpeedLimiterFeature:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as a side-effect-free Scaffold v2 input policy.
 */
package shit.zen.modules.impl.movement.scaffold.v2.motion;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;
import shit.zen.utils.game.DirectionalInput;

public final class SpeedLimiter {
    public static final float MIN_SPEED_LIMIT = 0.01f;
    public static final float MAX_SPEED_LIMIT = 0.4f;
    public static final Settings DEFAULTS = new Settings(false, 0.11f);

    private SpeedLimiter() {
    }

    public static Decision apply(
            DirectionalInput input,
            Vec3 velocity,
            Settings settings) {
        Objects.requireNonNull(velocity, "velocity");
        return apply(input, Math.hypot(velocity.x, velocity.z), settings);
    }

    public static Decision apply(
            DirectionalInput input,
            double horizontalSpeed,
            Settings settings) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(settings, "settings");

        boolean limited = settings.enabled() && horizontalSpeed > settings.speedLimit();
        return new Decision(limited ? DirectionalInput.NONE : input, limited);
    }

    public record Settings(boolean enabled, float speedLimit) {
        public Settings {
            if (!Float.isFinite(speedLimit)
                    || speedLimit < MIN_SPEED_LIMIT
                    || speedLimit > MAX_SPEED_LIMIT) {
                throw new IllegalArgumentException("speedLimit must be in 0.01..0.4");
            }
        }
    }

    public record Decision(DirectionalInput directionalInput, boolean limited) {
        public Decision {
            Objects.requireNonNull(directionalInput, "directionalInput");
        }
    }
}
