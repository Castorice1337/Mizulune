package shit.zen.render;

public final class LiquidGlassSettings {
    public static final float DEFAULT_RADIUS = 3.54f;
    public static final int DEFAULT_BLUR_ITERATIONS = 1;
    public static final float DEFAULT_BLUR_RADIUS = 2.02f;
    public static final float DEFAULT_BLUR_DOWNSCALE = 1.0f;
    public static final float DEFAULT_NOISE = 0.03f;
    public static final float DEFAULT_REFRACTION_POWER = 3.41f;
    public static final float DEFAULT_REFRACTION_STRENGTH = 1.0f;
    public static final float DEFAULT_OPACITY = 0.76f;
    public static final float DEFAULT_DARKNESS = 0.02f;

    private static volatile float radius = DEFAULT_RADIUS;
    private static volatile int blurIterations = DEFAULT_BLUR_ITERATIONS;
    private static volatile float blurRadius = DEFAULT_BLUR_RADIUS;
    private static volatile float blurDownscale = DEFAULT_BLUR_DOWNSCALE;
    private static volatile float noise = DEFAULT_NOISE;
    private static volatile float refractionPower = DEFAULT_REFRACTION_POWER;
    private static volatile float refractionStrength = DEFAULT_REFRACTION_STRENGTH;
    private static volatile float opacity = DEFAULT_OPACITY;
    private static volatile float darkness = DEFAULT_DARKNESS;

    private LiquidGlassSettings() {
    }

    public static void configure(float radius, int blurIterations, float blurRadius, float blurDownscale,
                                 float noise, float refractionPower, float refractionStrength,
                                 float opacity, float darkness) {
        LiquidGlassSettings.radius = clamp(radius, 1.0f, 8.0f);
        LiquidGlassSettings.blurIterations = Math.round(clamp(blurIterations, 0.0f, 4.0f));
        LiquidGlassSettings.blurRadius = clamp(blurRadius, 0.0f, 24.0f);
        LiquidGlassSettings.blurDownscale = clamp(blurDownscale, 0.25f, 2.0f);
        LiquidGlassSettings.noise = clamp(noise, 0.0f, 0.15f);
        LiquidGlassSettings.refractionPower = clamp(refractionPower, 0.1f, 6.0f);
        LiquidGlassSettings.refractionStrength = clamp(refractionStrength, 0.0f, 1.5f);
        LiquidGlassSettings.opacity = clamp(opacity, 0.0f, 1.0f);
        LiquidGlassSettings.darkness = clamp(darkness, 0.0f, 0.4f);
    }

    public static float getRadius() {
        return radius;
    }

    public static int getBlurIterations() {
        return blurIterations;
    }

    public static float getBlurRadius() {
        return blurRadius;
    }

    public static float getBlurDownscale() {
        return blurDownscale;
    }

    public static float getNoise() {
        return noise;
    }

    public static float getRefractionPower() {
        return refractionPower;
    }

    public static float getRefractionStrength() {
        return refractionStrength;
    }

    public static float getOpacity() {
        return opacity;
    }

    public static float getDarkness() {
        return darkness;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
