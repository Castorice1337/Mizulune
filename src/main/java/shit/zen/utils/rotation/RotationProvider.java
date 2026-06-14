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

    default boolean shouldHumanizeRotation() {
        return false;
    }

    default boolean shouldFixMovement() {
        return this.getApplyMode() == RotationApplyMode.SILENT;
    }

    default int getRotationPriority() {
        return 0;
    }
}
