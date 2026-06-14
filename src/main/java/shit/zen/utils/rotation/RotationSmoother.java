/*
 * Inspired by LiquidBounce Nextgen rotation target processing:
 * net.ccbluex.liquidbounce.utils.aiming.RotationManager
 * net.ccbluex.liquidbounce.utils.aiming.utils.Rotation
 * net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.*
 *
 * LiquidBounce is licensed under GPL-3.0-or-later.
 * Modified for Mizulune/OpenZen as a standalone stateful smoother with
 * SNAP, LINEAR, and angle-difference SIGMOID modes plus configurable caps.
 */
package shit.zen.utils.rotation;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.util.Mth;
import shit.zen.ClientBase;

public class RotationSmoother extends ClientBase {
    private Rotation currentRotation;
    private Rotation targetRotation;

    public Rotation update(
            Rotation target,
            SmoothMode mode,
            int durationTicks,
            double steepness,
            double maxYawSpeed,
            double maxPitchSpeed,
            double minStep,
            double epsilon,
            boolean humanize) {
        if (target == null || mc.player == null || mc.options == null) {
            this.reset();
            return null;
        }

        SmoothMode smoothMode = mode == null ? SmoothMode.SNAP : mode;
        Rotation fallback = new Rotation(mc.player.getYRot(), mc.player.getXRot());
        if (this.currentRotation == null) {
            this.currentRotation = this.clampPitch(fallback);
        }

        this.targetRotation = this.normalizeTarget(target, this.currentRotation);

        float yawDiff = Mth.wrapDegrees(this.targetRotation.getYaw() - this.currentRotation.getYaw());
        float pitchDiff = this.targetRotation.getPitch() - this.currentRotation.getPitch();
        double gcd = this.getSensitivityStep();
        double reachEpsilon = Math.max(Math.max(0.0, epsilon), gcd * 0.5);
        if (this.isClose(yawDiff, pitchDiff, reachEpsilon)) {
            this.currentRotation = this.snapDelta(this.currentRotation, this.targetRotation, false);
            return this.currentRotation.clone();
        }

        if (smoothMode == SmoothMode.SNAP) {
            this.currentRotation = this.snapDelta(this.currentRotation, this.targetRotation, true);
            return this.currentRotation.clone();
        }

        double distance = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
        double[] speeds = this.resolveSpeeds(smoothMode, distance, durationTicks, steepness, maxYawSpeed, maxPitchSpeed);
        float yawStep = this.resolveAxisStep(yawDiff, distance, speeds[0], minStep, reachEpsilon);
        float pitchStep = this.resolveAxisStep(pitchDiff, distance, speeds[1], minStep, reachEpsilon);

        Rotation next = new Rotation(
                this.currentRotation.getYaw() + yawStep,
                Mth.clamp(this.currentRotation.getPitch() + pitchStep, -90.0f, 90.0f));
        if (humanize && !this.isClose(yawDiff, pitchDiff, reachEpsilon * 2.0)) {
            next.setYaw(next.getYaw() + ThreadLocalRandom.current().nextFloat(-0.015f, 0.015f));
            next.setPitch(Mth.clamp(next.getPitch() + ThreadLocalRandom.current().nextFloat(-0.01f, 0.01f), -90.0f, 90.0f));
        }

        next = this.snapDelta(this.currentRotation, next, true);
        float snappedYawDiff = Mth.wrapDegrees(this.targetRotation.getYaw() - next.getYaw());
        float snappedPitchDiff = this.targetRotation.getPitch() - next.getPitch();
        if (this.isClose(snappedYawDiff, snappedPitchDiff, reachEpsilon)) {
            next = this.snapDelta(next, this.targetRotation, false);
        }

        this.currentRotation = next;
        return this.currentRotation.clone();
    }

    public Rotation getCurrentRotation() {
        return this.currentRotation == null ? null : this.currentRotation.clone();
    }

    public void reset() {
        this.currentRotation = null;
        this.targetRotation = null;
    }

    private Rotation normalizeTarget(Rotation target, Rotation reference) {
        Rotation base = reference == null ? new Rotation(mc.player.getYRot(), mc.player.getXRot()) : reference;
        float yaw = base.getYaw() + Mth.wrapDegrees(target.getYaw() - base.getYaw());
        float pitch = Mth.clamp(target.getPitch(), -90.0f, 90.0f);
        return new Rotation(yaw, pitch);
    }

    private Rotation clampPitch(Rotation rotation) {
        return new Rotation(rotation.getYaw(), Mth.clamp(rotation.getPitch(), -90.0f, 90.0f));
    }

    private double[] resolveSpeeds(
            SmoothMode mode,
            double distance,
            int durationTicks,
            double steepness,
            double maxYawSpeed,
            double maxPitchSpeed) {
        double durationScale = Math.max(0.1, 6.0 / Math.max(1, durationTicks));
        double yawSpeed = Math.max(0.0, maxYawSpeed) * durationScale;
        double pitchSpeed = Math.max(0.0, maxPitchSpeed) * durationScale;
        if (mode == SmoothMode.SIGMOID) {
            double scaledDifference = distance / 120.0;
            double sigmoid = sigmoidByDifference(scaledDifference, steepness);
            yawSpeed *= sigmoid;
            pitchSpeed *= sigmoid;
        }
        return new double[]{yawSpeed, pitchSpeed};
    }

    private float resolveAxisStep(double remaining, double distance, double speed, double minStep, double epsilon) {
        double absRemaining = Math.abs(remaining);
        if (absRemaining <= epsilon || distance <= 1.0E-6) {
            return (float) remaining;
        }
        double factor = absRemaining / distance;
        double step = Math.max(0.0, speed) * factor;
        double minimum = Math.max(0.0, minStep);
        if (step < minimum) {
            step = minimum;
        }
        if (step > absRemaining) {
            step = absRemaining;
        }
        return (float) Math.copySign(step, remaining);
    }

    private Rotation snapDelta(Rotation from, Rotation intended, boolean forceMinimumStep) {
        double step = this.getSensitivityStep();
        if (step <= 1.0E-6) {
            return this.clampPitch(intended);
        }
        float yawDelta = Mth.wrapDegrees(intended.getYaw() - from.getYaw());
        float pitchDelta = intended.getPitch() - from.getPitch();
        float snappedYawDelta = snapDeltaAxis(yawDelta, step, forceMinimumStep);
        float snappedPitchDelta = snapDeltaAxis(pitchDelta, step, forceMinimumStep);
        return new Rotation(
                from.getYaw() + snappedYawDelta,
                Mth.clamp(from.getPitch() + snappedPitchDelta, -90.0f, 90.0f));
    }

    private double getSensitivityStep() {
        float sensitivity = mc.options.sensitivity().get().floatValue();
        float scaled = sensitivity * 0.6f + 0.2f;
        return scaled * scaled * scaled * 1.2f;
    }

    private static float snapDeltaAxis(float delta, double step, boolean forceMinimumStep) {
        if (Math.abs(delta) <= 1.0E-6) {
            return 0.0f;
        }
        double snapped = Math.round(delta / step) * step;
        if (forceMinimumStep && Math.abs(snapped) <= 1.0E-6) {
            snapped = Math.copySign(step, delta);
        }
        return (float) snapped;
    }

    private boolean isClose(double yawDiff, double pitchDiff, double epsilon) {
        return Math.abs(yawDiff) <= epsilon && Math.abs(pitchDiff) <= epsilon;
    }

    private static double sigmoidByDifference(double scaledDifference, double steepness) {
        double safeSteepness = Math.max(0.001, steepness);
        double midpoint = 0.5;
        return 1.0 / (1.0 + Math.exp(-safeSteepness * (Mth.clamp(scaledDifference, 0.0, 1.0) - midpoint)));
    }
}
