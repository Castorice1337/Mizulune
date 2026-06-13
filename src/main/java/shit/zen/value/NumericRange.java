package shit.zen.value;

import net.minecraft.util.Mth;

public final class NumericRange {
    private final double lower;
    private final double upper;
    private final double min;
    private final double max;
    private final double step;
    private final boolean integer;

    public NumericRange(double lower, double upper, double min, double max, double step, boolean integer) {
        this.min = min;
        this.max = max;
        this.step = step;
        this.integer = integer;
        double clampedLower = clamp(lower);
        double clampedUpper = clamp(upper);
        this.lower = Math.min(clampedLower, clampedUpper);
        this.upper = Math.max(clampedLower, clampedUpper);
    }

    public double lower() {
        return this.lower;
    }

    public double upper() {
        return this.upper;
    }

    public double min() {
        return this.min;
    }

    public double max() {
        return this.max;
    }

    public double step() {
        return this.step;
    }

    public boolean integer() {
        return this.integer;
    }

    public double clamp(double value) {
        double clamped = Mth.clamp(value, this.min, this.max);
        if (this.integer) {
            return Math.round(clamped);
        }
        return clamped;
    }
}
