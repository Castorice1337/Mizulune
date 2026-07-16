/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ModuleSafeWalk:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as a stateful policy over immutable input snapshots.
 */
package shit.zen.modules.impl.movement.scaffold.v2;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import shit.zen.utils.game.DirectionalInput;
import shit.zen.utils.game.EdgeSafetyUtil;

public final class ScaffoldSafeWalkPolicy {
    static final double FAST_CENTER_SPEED = 0.05;
    static final float CENTER_DEAD_ANGLE = 20.0f;

    private Vec3 center;
    private int keepTicks;
    private int sneakTicks;

    public Decision update(Settings settings, Frame frame) {
        Settings effectiveSettings = settings == null ? Settings.none() : settings;
        Frame effectiveFrame = frame == null ? Frame.empty() : frame;

        if (effectiveSettings.mode() == Mode.NONE) {
            this.reset();
            return Decision.unchanged(effectiveFrame, false, this);
        }
        if (effectiveSettings.mode() == Mode.SAFE) {
            this.reset();
            return Decision.unchanged(effectiveFrame, true, this);
        }

        boolean triggered = false;
        boolean shouldBeActive = effectiveFrame.onGround() && !effectiveFrame.sneak();
        if (shouldBeActive && effectiveFrame.closeToEdge()) {
            if (this.center != null
                    && effectiveFrame.position() != null
                    && effectiveFrame.nextPosition() != null) {
                double currentDistance = horizontalDistanceSqr(
                        this.center,
                        effectiveFrame.position());
                double nextDistance = horizontalDistanceSqr(
                        this.center,
                        effectiveFrame.nextPosition());
                if (nextDistance <= currentDistance) {
                    return Decision.unchanged(effectiveFrame, false, this);
                }
            }

            if (this.keepTicks == 0) {
                this.keepTicks = effectiveSettings.keep().sample();
            }
            if (this.sneakTicks == 0) {
                this.sneakTicks = effectiveSettings.sneak().sample();
            }
            triggered = true;
        }

        DirectionalInput input = effectiveFrame.directionalInput();
        boolean jump = effectiveFrame.jump();
        boolean sneak = effectiveFrame.sneak();

        if (this.keepTicks > 0) {
            this.keepTicks--;
            switch (effectiveSettings.onEdgeMode()) {
                case INVERT -> {
                    input = invert(input);
                    jump = false;
                }
                case CENTER -> input = this.inputTowardsCenter(effectiveFrame);
                case STOP -> {
                    if (effectiveFrame.horizontalSpeed() > FAST_CENTER_SPEED) {
                        input = this.inputTowardsCenter(effectiveFrame);
                    } else {
                        input = DirectionalInput.NONE;
                        jump = false;
                    }
                }
            }

            if (effectiveSettings.jump()) {
                jump = true;
            }
        }

        if (this.sneakTicks > 0) {
            this.sneakTicks--;
            sneak = true;
        }

        if (effectiveFrame.blockCenterSafe() && effectiveFrame.blockCenter() != null) {
            this.center = effectiveFrame.blockCenter();
        }

        return new Decision(
                input,
                jump,
                sneak,
                false,
                triggered,
                this.center,
                this.keepTicks,
                this.sneakTicks);
    }

    public void reset() {
        this.center = null;
        this.keepTicks = 0;
        this.sneakTicks = 0;
    }

    public Vec3 center() {
        return this.center;
    }

    public int keepTicks() {
        return this.keepTicks;
    }

    public int sneakTicks() {
        return this.sneakTicks;
    }

    public static boolean isCloseToEdge(
            AABB playerBox,
            DirectionalInput input,
            float playerYaw,
            double horizontalSpeed,
            double edgeDistance) {
        if (playerBox == null) {
            return false;
        }

        DirectionalInput effectiveInput = input == null ? DirectionalInput.NONE : input;
        EdgeSafetyUtil.MovementVector movement = EdgeSafetyUtil.getMovementVectorFromInput(
                playerYaw,
                Math.round(effectiveInput.forwardImpulse()),
                Math.round(effectiveInput.strafeImpulse()));
        double directionalDistance = movement == null
                ? Double.POSITIVE_INFINITY
                : EdgeSafetyUtil.getDistanceToFall(playerBox, movement);
        return isCloseToEdge(
                EdgeSafetyUtil.getDistanceToFall(playerBox),
                directionalDistance,
                horizontalSpeed,
                edgeDistance);
    }

    static boolean isCloseToEdge(
            double globalDistance,
            double directionalDistance,
            double horizontalSpeed,
            double edgeDistance) {
        double threshold = Math.min(
                Math.max(0.0, horizontalSpeed),
                Math.max(0.0, edgeDistance));
        return Math.max(0.0, globalDistance) <= threshold
                || Math.max(0.0, directionalDistance) <= threshold;
    }

    static DirectionalInput invert(DirectionalInput input) {
        DirectionalInput effectiveInput = input == null ? DirectionalInput.NONE : input;
        return new DirectionalInput(
                effectiveInput.backwards(),
                effectiveInput.forwards(),
                effectiveInput.right(),
                effectiveInput.left());
    }

    static DirectionalInput getDirectionalInputForDegrees(float degrees) {
        boolean forwards = degrees >= -90.0f + CENTER_DEAD_ANGLE
                && degrees <= 90.0f - CENTER_DEAD_ANGLE;
        boolean backwards = degrees < -90.0f - CENTER_DEAD_ANGLE
                || degrees > 90.0f + CENTER_DEAD_ANGLE;
        boolean right = degrees >= CENTER_DEAD_ANGLE
                && degrees <= 180.0f - CENTER_DEAD_ANGLE;
        boolean left = degrees >= -180.0f + CENTER_DEAD_ANGLE
                && degrees <= -CENTER_DEAD_ANGLE;
        return new DirectionalInput(forwards, backwards, left, right);
    }

    private DirectionalInput inputTowardsCenter(Frame frame) {
        Vec3 target = this.center == null ? frame.blockCenter() : this.center;
        if (target == null || frame.position() == null) {
            return DirectionalInput.NONE;
        }
        Vec3 relative = target.subtract(frame.position());
        float optimalYaw = (float) Math.atan2(-relative.x, relative.z);
        float currentYaw = Mth.wrapDegrees(frame.yaw()) * Mth.DEG_TO_RAD;
        float degrees = Mth.wrapDegrees((optimalYaw - currentYaw) / Mth.DEG_TO_RAD);
        return getDirectionalInputForDegrees(degrees);
    }

    private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return x * x + z * z;
    }

    public enum Mode {
        NONE,
        SAFE,
        ON_EDGE
    }

    public enum OnEdgeMode {
        STOP,
        INVERT,
        CENTER
    }

    public record TickRange(int minimum, int maximum) {
        public TickRange {
            if (minimum < 0 || maximum < minimum) {
                throw new IllegalArgumentException("Invalid tick range: " + minimum + ".." + maximum);
            }
        }

        public static TickRange fixed(int ticks) {
            return new TickRange(ticks, ticks);
        }

        int sample() {
            if (this.minimum == this.maximum) {
                return this.minimum;
            }
            return ThreadLocalRandom.current().nextInt(this.minimum, this.maximum + 1);
        }
    }

    public record Settings(
            Mode mode,
            double edgeDistance,
            TickRange keep,
            OnEdgeMode onEdgeMode,
            TickRange sneak,
            boolean jump) {
        public Settings {
            mode = mode == null ? Mode.NONE : mode;
            edgeDistance = Double.isFinite(edgeDistance) ? Math.max(0.0, edgeDistance) : 0.0;
            keep = keep == null ? TickRange.fixed(0) : keep;
            onEdgeMode = onEdgeMode == null ? OnEdgeMode.STOP : onEdgeMode;
            sneak = sneak == null ? TickRange.fixed(0) : sneak;
        }

        public static Settings none() {
            return new Settings(
                    Mode.NONE,
                    0.1,
                    TickRange.fixed(0),
                    OnEdgeMode.STOP,
                    TickRange.fixed(0),
                    false);
        }

        public static Settings safe() {
            return new Settings(
                    Mode.SAFE,
                    0.1,
                    TickRange.fixed(0),
                    OnEdgeMode.STOP,
                    TickRange.fixed(0),
                    false);
        }
    }

    public record Frame(
            DirectionalInput directionalInput,
            boolean jump,
            boolean sneak,
            boolean onGround,
            boolean closeToEdge,
            double horizontalSpeed,
            Vec3 position,
            Vec3 nextPosition,
            float yaw,
            Vec3 blockCenter,
            boolean blockCenterSafe) {
        public Frame {
            directionalInput = directionalInput == null ? DirectionalInput.NONE : directionalInput;
            horizontalSpeed = Double.isFinite(horizontalSpeed) ? Math.max(0.0, horizontalSpeed) : 0.0;
        }

        static Frame empty() {
            return new Frame(
                    DirectionalInput.NONE,
                    false,
                    false,
                    false,
                    false,
                    0.0,
                    null,
                    null,
                    0.0f,
                    null,
                    false);
        }
    }

    public record Decision(
            DirectionalInput directionalInput,
            boolean jump,
            boolean sneak,
            boolean safeWalk,
            boolean edgeTriggered,
            Vec3 center,
            int keepTicksRemaining,
            int sneakTicksRemaining) {
        private static Decision unchanged(
                Frame frame,
                boolean safeWalk,
                ScaffoldSafeWalkPolicy policy) {
            return new Decision(
                    frame.directionalInput(),
                    frame.jump(),
                    frame.sneak(),
                    safeWalk,
                    false,
                    policy.center,
                    policy.keepTicks,
                    policy.sneakTicks);
        }
    }
}
