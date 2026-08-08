/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ModuleScaffold.SimulatePlacementAttempts and
 * ModuleScaffold.simulatePlacementAttempts:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as a side-effect-free Scaffold v2 attempt policy.
 */
package shit.zen.modules.impl.movement.scaffold.v2.motion;

import java.util.Objects;
import java.util.random.RandomGenerator;

public final class SimulatePlacementAttempts {
    public static final int MIN_CPS = 1;
    public static final int MAX_CPS = 100;
    public static final CpsRange DEFAULT_CPS = new CpsRange(5, 8);
    public static final Settings DEFAULTS = new Settings(false, DEFAULT_CPS, true);

    private SimulatePlacementAttempts() {
    }

    public static boolean shouldAttempt(AttemptInput input, Settings settings) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(settings, "settings");

        boolean simulate = shouldSimulate(input.placement(), settings);
        return simulate
                && input.moving()
                && input.clickTick();
    }

    public static boolean shouldSimulate(PlacementInput input, Settings settings) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(settings, "settings");

        if (!input.hasSuitableHand()
                || !settings.enabled()
                || !input.hasHitResult()
                || !input.blockHit()) {
            return false;
        }

        if (settings.failedAttemptsOnly()) {
            return !input.canPlaceOnFace();
        }

        if (input.sameYEnabled()) {
            return input.clickedY() == input.placementY()
                    && (!input.clickedFaceUp() || !input.canPlaceOnFace());
        }

        boolean targetUnderPlayer = input.clickedY() <= input.playerBlockY() - 1;
        boolean towering = input.clickedY() == input.playerBlockY() - 1
                && input.canPlaceOnFace()
                && input.clickedFaceUp();
        return targetUnderPlayer && !towering;
    }

    public record Settings(boolean enabled, CpsRange cps, boolean failedAttemptsOnly) {
        public Settings {
            Objects.requireNonNull(cps, "cps");
        }
    }

    public record CpsRange(int minimum, int maximum) {
        public CpsRange {
            if (minimum < MIN_CPS || maximum > MAX_CPS || minimum > maximum) {
                throw new IllegalArgumentException("CPS range must be ordered within 1..100");
            }
        }

        public int sample(RandomGenerator random) {
            Objects.requireNonNull(random, "random");
            return this.minimum == this.maximum
                    ? this.minimum
                    : random.nextInt(this.minimum, this.maximum + 1);
        }
    }

    public record AttemptInput(PlacementInput placement, boolean moving, boolean clickTick) {
        public AttemptInput {
            Objects.requireNonNull(placement, "placement");
        }
    }

    public record PlacementInput(
            boolean hasSuitableHand,
            boolean hasHitResult,
            boolean blockHit,
            boolean canPlaceOnFace,
            boolean sameYEnabled,
            int clickedY,
            int placementY,
            boolean clickedFaceUp,
            int playerBlockY) {
    }
}
