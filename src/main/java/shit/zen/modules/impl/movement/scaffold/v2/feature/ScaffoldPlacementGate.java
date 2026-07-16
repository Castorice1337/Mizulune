/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ModuleScaffold Delay and MinDist gates:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 for Mizulune's Java/Forge 1.20.1 Scaffold v2 policies.
 */
package shit.zen.modules.impl.movement.scaffold.v2.feature;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class ScaffoldPlacementGate {
    public static final int MIN_DELAY_TICKS = 0;
    public static final int MAX_DELAY_TICKS = 40;
    public static final double MIN_DISTANCE = 0.0;
    public static final double MAX_DISTANCE = 0.25;
    public static final Settings DEFAULTS = new Settings(0, 0, 0.0);

    private long nextAllowedTick = Long.MIN_VALUE;

    public boolean canAttempt(long currentTick) {
        return currentTick >= this.nextAllowedTick;
    }

    public int onPlacementSucceeded(long currentTick, Settings settings) {
        return this.onPlacementSucceeded(currentTick, settings, ThreadLocalRandom.current());
    }

    public int onPlacementSucceeded(
            long currentTick,
            Settings settings,
            RandomGenerator random) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(random, "random");

        int sampledDelay = settings.sampleDelay(random);
        this.onPlacementSucceeded(currentTick, sampledDelay);
        return sampledDelay;
    }

    public void onPlacementSucceeded(long currentTick, int sampledDelay) {
        if (sampledDelay < MIN_DELAY_TICKS || sampledDelay > MAX_DELAY_TICKS) {
            throw new IllegalArgumentException("sampledDelay must be in 0..40");
        }
        long delayWithCurrentTick = sampledDelay + 1L;
        this.nextAllowedTick = currentTick > Long.MAX_VALUE - delayWithCurrentTick
                ? Long.MAX_VALUE
                : currentTick + delayWithCurrentTick;
    }

    public void reset() {
        this.nextAllowedTick = Long.MIN_VALUE;
    }

    public long nextAllowedTick() {
        return this.nextAllowedTick;
    }

    public static boolean passesMinDistance(
            Direction side,
            Vec3 eyePosition,
            Vec3 hitPosition,
            Settings settings) {
        Objects.requireNonNull(settings, "settings");
        return passesMinDistance(side, eyePosition, hitPosition, settings.minDistance());
    }

    public static boolean passesMinDistance(
            Direction side,
            Vec3 eyePosition,
            Vec3 hitPosition,
            double minDistance) {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(eyePosition, "eyePosition");
        Objects.requireNonNull(hitPosition, "hitPosition");
        validateMinDistance(minDistance);

        if (side.getAxis() == Direction.Axis.Y) {
            return true;
        }

        Vec3 difference = hitPosition.subtract(eyePosition);
        double distance = side == Direction.NORTH || side == Direction.SOUTH
                ? difference.z
                : difference.x;
        return Math.abs(distance) >= minDistance;
    }

    private static void validateMinDistance(double minDistance) {
        if (!Double.isFinite(minDistance)
                || minDistance < MIN_DISTANCE
                || minDistance > MAX_DISTANCE) {
            throw new IllegalArgumentException("minDistance must be in 0.0..0.25");
        }
    }

    public record Settings(int delayMinTicks, int delayMaxTicks, double minDistance) {
        public Settings {
            if (delayMinTicks < MIN_DELAY_TICKS || delayMinTicks > MAX_DELAY_TICKS) {
                throw new IllegalArgumentException("delayMinTicks must be in 0..40");
            }
            if (delayMaxTicks < MIN_DELAY_TICKS || delayMaxTicks > MAX_DELAY_TICKS) {
                throw new IllegalArgumentException("delayMaxTicks must be in 0..40");
            }
            if (delayMinTicks > delayMaxTicks) {
                throw new IllegalArgumentException("delayMinTicks cannot exceed delayMaxTicks");
            }
            validateMinDistance(minDistance);
        }

        public int sampleDelay(RandomGenerator random) {
            Objects.requireNonNull(random, "random");
            return this.delayMinTicks == this.delayMaxTicks
                    ? this.delayMinTicks
                    : random.nextInt(this.delayMinTicks, this.delayMaxTicks + 1);
        }
    }
}
