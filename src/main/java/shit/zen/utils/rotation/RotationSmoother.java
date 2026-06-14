/*
 * Inspired by LiquidBounce Nextgen rotation target processing:
 * net.ccbluex.liquidbounce.utils.aiming.RotationManager
 *
 * LiquidBounce is licensed under GPL-3.0-or-later.
 * Modified for Mizulune/OpenZen as a standalone stateful smoother with
 * SNAP, LINEAR, and normalized SIGMOID modes.
 */
package shit.zen.utils.rotation;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.util.Mth;
import shit.zen.ClientBase;

public class RotationSmoother extends ClientBase {
    private Rotation currentRotation;
    private Rotation startRotation;
    private Rotation targetRotation;
    private SmoothMode activeMode = SmoothMode.SNAP;
    private int elapsedTicks;

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
            this.currentRotation = this.snap(this.clampPitch(fallback));
            this.startRotation = this.currentRotation.clone();
        }

        Rotation normalizedTarget = this.normalizeTarget(target, this.targetRotation != null ? this.targetRotation : this.currentRotation);
        if (this.hasTargetChanged(normalizedTarget, smoothMode, epsilon)) {
            this.startRotation = this.currentRotation.clone();
            this.targetRotation = normalizedTarget;
            this.activeMode = smoothMode;
            this.elapsedTicks = 0;
        }

        if (this.targetRotation == null) {
            this.targetRotation = normalizedTarget;
        }

        if (smoothMode == SmoothMode.SNAP) {
            this.currentRotation = this.targetRotation.clone();
            return this.currentRotation.clone();
        }

        float yawDiff = Mth.wrapDegrees(this.targetRotation.getYaw() - this.currentRotation.getYaw());
        float pitchDiff = this.targetRotation.getPitch() - this.currentRotation.getPitch();
        double reachEpsilon = Math.max(0.0, epsilon);
        if (Math.abs(yawDiff) <= reachEpsilon && Math.abs(pitchDiff) <= reachEpsilon) {
            this.currentRotation = this.targetRotation.clone();
            return this.currentRotation.clone();
        }

        double progress = Math.min(1.0, (this.elapsedTicks + 1.0) / Math.max(1, durationTicks));
        double eased = smoothMode == SmoothMode.SIGMOID
                ? normalizedSigmoid(progress, steepness)
                : progress;
        float targetYaw = this.startRotation.getYaw()
                + (float) (Mth.wrapDegrees(this.targetRotation.getYaw() - this.startRotation.getYaw()) * eased);
        float targetPitch = this.startRotation.getPitch()
                + (float) ((this.targetRotation.getPitch() - this.startRotation.getPitch()) * eased);

        float yawStep = Mth.wrapDegrees(targetYaw - this.currentRotation.getYaw());
        float pitchStep = targetPitch - this.currentRotation.getPitch();
        yawStep = this.clampStep(yawStep, yawDiff, maxYawSpeed, minStep, reachEpsilon);
        pitchStep = this.clampStep(pitchStep, pitchDiff, maxPitchSpeed, minStep, reachEpsilon);

        Rotation next = new Rotation(
                this.currentRotation.getYaw() + yawStep,
                Mth.clamp(this.currentRotation.getPitch() + pitchStep, -90.0f, 90.0f));
        if (humanize && progress > 0.0 && progress < 1.0) {
            next.setYaw(next.getYaw() + ThreadLocalRandom.current().nextFloat(-0.015f, 0.015f));
            next.setPitch(Mth.clamp(next.getPitch() + ThreadLocalRandom.current().nextFloat(-0.01f, 0.01f), -90.0f, 90.0f));
        }

        next = this.snap(next);
        float snappedYawDiff = Mth.wrapDegrees(this.targetRotation.getYaw() - next.getYaw());
        float snappedPitchDiff = this.targetRotation.getPitch() - next.getPitch();
        if (Math.abs(snappedYawDiff) <= reachEpsilon && Math.abs(snappedPitchDiff) <= reachEpsilon) {
            next = this.targetRotation.clone();
        }

        this.currentRotation = next;
        this.elapsedTicks++;
        return this.currentRotation.clone();
    }

    public Rotation getCurrentRotation() {
        return this.currentRotation == null ? null : this.currentRotation.clone();
    }

    public void reset() {
        this.currentRotation = null;
        this.startRotation = null;
        this.targetRotation = null;
        this.activeMode = SmoothMode.SNAP;
        this.elapsedTicks = 0;
    }

    private boolean hasTargetChanged(Rotation target, SmoothMode mode, double epsilon) {
        if (this.targetRotation == null || this.activeMode != mode) {
            return true;
        }
        double threshold = Math.max(0.001, epsilon * 0.5);
        return Math.abs(Mth.wrapDegrees(target.getYaw() - this.targetRotation.getYaw())) > threshold
                || Math.abs(target.getPitch() - this.targetRotation.getPitch()) > threshold;
    }

    private Rotation normalizeTarget(Rotation target, Rotation reference) {
        Rotation base = reference == null ? new Rotation(mc.player.getYRot(), mc.player.getXRot()) : reference;
        float yaw = base.getYaw() + Mth.wrapDegrees(target.getYaw() - base.getYaw());
        float pitch = Mth.clamp(target.getPitch(), -90.0f, 90.0f);
        return this.snap(new Rotation(yaw, pitch));
    }

    private Rotation clampPitch(Rotation rotation) {
        return new Rotation(rotation.getYaw(), Mth.clamp(rotation.getPitch(), -90.0f, 90.0f));
    }

    private Rotation snap(Rotation rotation) {
        return rotation.snapToSensitivity(mc.options.sensitivity().get().floatValue());
    }

    private float clampStep(double step, double remaining, double maxSpeed, double minStep, double epsilon) {
        if (Math.abs(remaining) <= epsilon) {
            return (float) remaining;
        }
        double limited = step;
        if (maxSpeed > 0.0) {
            limited = Mth.clamp(limited, -maxSpeed, maxSpeed);
        }
        if (Math.abs(limited) < minStep) {
            limited = Math.copySign(Math.min(Math.abs(remaining), minStep), remaining);
        }
        if (Math.abs(limited) > Math.abs(remaining)) {
            limited = remaining;
        }
        return (float) limited;
    }

    private static double normalizedSigmoid(double progress, double steepness) {
        double safeSteepness = Math.max(0.001, steepness);
        double start = sigmoid(0.0, safeSteepness);
        double end = sigmoid(1.0, safeSteepness);
        double value = sigmoid(Mth.clamp(progress, 0.0, 1.0), safeSteepness);
        return (value - start) / (end - start);
    }

    private static double sigmoid(double value, double steepness) {
        return 1.0 / (1.0 + Math.exp(-steepness * (value - 0.5)));
    }
}
