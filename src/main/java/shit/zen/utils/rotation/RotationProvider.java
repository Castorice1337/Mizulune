package shit.zen.utils.rotation;

public interface RotationProvider {
    String getName();

    Rotation getRotation();

    boolean isRotationActive();

    default RotationApplyMode getApplyMode() {
        return RotationApplyMode.SILENT;
    }

    default SmoothMode getSmoothMode() {
        return SmoothMode.SNAP;
    }

    default int getSmoothDurationTicks() {
        return 1;
    }

    default double getSmoothSteepness() {
        return 8.0;
    }

    default double getMaxYawSpeed() {
        return 180.0;
    }

    default double getMaxPitchSpeed() {
        return 90.0;
    }

    default double getMinStep() {
        return 0.05;
    }

    default double getRotationEpsilon() {
        return 0.1;
    }

    default double getInterpolationHorizontalSpeedMin() {
        return 0.80;
    }

    default double getInterpolationHorizontalSpeedMax() {
        return 0.85;
    }

    default double getInterpolationVerticalSpeedMin() {
        return 0.20;
    }

    default double getInterpolationVerticalSpeedMax() {
        return 0.25;
    }

    default double getInterpolationDirectionChangeFactorMin() {
        return 0.95;
    }

    default double getInterpolationDirectionChangeFactorMax() {
        return 1.0;
    }

    default double getInterpolationMidpoint() {
        return 0.35;
    }

    default boolean shouldHumanizeRotation() {
        return false;
    }

    default boolean shouldFixMovement() {
        return this.getApplyMode() == RotationApplyMode.SILENT;
    }

    default int getTicksUntilReset() {
        return 3;
    }

    default double getResetThreshold() {
        return 1.0;
    }

    default boolean shouldResetRotation() {
        return this.getApplyMode() == RotationApplyMode.SILENT;
    }

    default boolean shouldAffectRayTrace() {
        return true;
    }

    default boolean shouldAffectUseItemRayTrace() {
        return this.shouldAffectRayTrace();
    }

    default int getRotationPriority() {
        return 0;
    }
}
