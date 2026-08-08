/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldTellyFeature:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as a stateless adapter over the existing timing counters.
 */
package shit.zen.modules.impl.movement.scaffold.v2.normal;

import java.util.Objects;
import java.util.random.RandomGenerator;

public final class ScaffoldTellyFeature {
    public static final int MIN_STRAIGHT_TICKS = 0;
    public static final int MAX_STRAIGHT_TICKS = 5;
    public static final int MIN_JUMP_TICKS = 0;
    public static final int MAX_JUMP_TICKS = 10;
    public static final Settings DEFAULTS = new Settings(
            false,
            ResetMode.RESET,
            0,
            new JumpTickRange(0, 0),
            true);

    private ScaffoldTellyFeature() {
    }

    public static Decision decide(Settings settings, TimingFrame frame) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(frame, "frame");

        boolean straight = !frame.hasCurrentRotation() || settings.straightTicks() == 0;
        boolean jump = frame.jump();
        if (settings.enabled()
                && frame.moving()
                && frame.blockCount() > 0
                && frame.onGround()) {
            jump = switch (settings.resetMode()) {
                case REVERSE -> true;
                case RESET -> jump || straight
                        && frame.ticksUntilJump() >= frame.sampledJumpTicks();
            };
        }

        boolean timingWindow = frame.airTicks() <= settings.straightTicks()
                && frame.ticksUntilJump() >= frame.sampledJumpTicks();
        boolean doNotAim = settings.enabled()
                && timingWindow
                && !(frame.towering() && settings.aimOnTower());
        boolean tellyBridging = settings.enabled()
                && frame.ticksUntilJump() >= frame.sampledJumpTicks()
                && frame.moving();
        return new Decision(jump, doNotAim, tellyBridging, straight);
    }

    public enum ResetMode {
        RESET,
        REVERSE
    }

    public record JumpTickRange(int minimum, int maximum) {
        public JumpTickRange {
            if (minimum < MIN_JUMP_TICKS
                    || maximum > MAX_JUMP_TICKS
                    || minimum > maximum) {
                throw new IllegalArgumentException("jumpTicks must be an ordered range in 0..10");
            }
        }

        public int sample(RandomGenerator random) {
            Objects.requireNonNull(random, "random");
            return this.minimum == this.maximum
                    ? this.minimum
                    : random.nextInt(this.minimum, this.maximum + 1);
        }
    }

    public record Settings(
            boolean enabled,
            ResetMode resetMode,
            int straightTicks,
            JumpTickRange jumpTicks,
            boolean aimOnTower) {
        public Settings {
            Objects.requireNonNull(resetMode, "resetMode");
            Objects.requireNonNull(jumpTicks, "jumpTicks");
            if (straightTicks < MIN_STRAIGHT_TICKS || straightTicks > MAX_STRAIGHT_TICKS) {
                throw new IllegalArgumentException("straightTicks must be in 0..5");
            }
        }
    }

    public record TimingFrame(
            boolean jump,
            boolean moving,
            int blockCount,
            boolean onGround,
            boolean hasCurrentRotation,
            int airTicks,
            int ticksUntilJump,
            int sampledJumpTicks,
            boolean towering) {
        public TimingFrame {
            if (airTicks < 0 || ticksUntilJump < 0) {
                throw new IllegalArgumentException("timing counters must be non-negative");
            }
            if (sampledJumpTicks < MIN_JUMP_TICKS || sampledJumpTicks > MAX_JUMP_TICKS) {
                throw new IllegalArgumentException("sampledJumpTicks must be in 0..10");
            }
        }
    }

    public record Decision(
            boolean jump,
            boolean doNotAim,
            boolean tellyBridging,
            boolean straight) {
    }
}
