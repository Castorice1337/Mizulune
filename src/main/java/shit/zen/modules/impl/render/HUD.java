package shit.zen.modules.impl.render;

import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.render.LiquidGlassSettings;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;

public class HUD extends Module {
    public Value<Number> liquidGlassRadius;
    public Value<Number> liquidGlassBlurIterations;
    public Value<Number> liquidGlassBlurRadius;
    public Value<Number> liquidGlassBlurDownscale;
    public Value<Number> liquidGlassNoise;
    public Value<Number> liquidGlassRefractionPower;
    public Value<Number> liquidGlassRefractionStrength;
    public Value<Number> liquidGlassOpacity;
    public Value<Number> liquidGlassDarkness;

    public HUD() {
        super("HUD", Category.RENDER);
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup liquidGlass = root.group("liquid_glass", "Liquid Glass");
        this.liquidGlassRadius = listen(liquidGlass.decimal(
                "radius", "Radius", LiquidGlassSettings.DEFAULT_RADIUS, 1.0f, 8.0f, 0.01f)
                .alias("Liquid Glass Radius"));
        this.liquidGlassBlurIterations = listen(liquidGlass.integer(
                "blur_iterations", "Blur Iterations", LiquidGlassSettings.DEFAULT_BLUR_ITERATIONS, 0, 4, 1)
                .alias("Liquid Glass Blur Iterations"));
        this.liquidGlassBlurRadius = listen(liquidGlass.decimal(
                "blur_radius", "Blur Radius", LiquidGlassSettings.DEFAULT_BLUR_RADIUS, 0.0f, 24.0f, 0.01f)
                .alias("Liquid Glass Blur Radius"));
        this.liquidGlassBlurDownscale = listen(liquidGlass.decimal(
                "blur_downscale", "Blur Downscale", LiquidGlassSettings.DEFAULT_BLUR_DOWNSCALE, 0.25f, 2.0f, 0.05f)
                .alias("Liquid Glass Blur Downscale"));
        this.liquidGlassNoise = listen(liquidGlass.decimal(
                "noise", "Noise", LiquidGlassSettings.DEFAULT_NOISE, 0.0f, 0.15f, 0.001f)
                .alias("Liquid Glass Noise"));
        this.liquidGlassRefractionPower = listen(liquidGlass.decimal(
                "refraction_power", "Refraction Power", LiquidGlassSettings.DEFAULT_REFRACTION_POWER, 0.1f, 6.0f, 0.01f)
                .alias("Liquid Glass Refraction Power"));
        this.liquidGlassRefractionStrength = listen(liquidGlass.decimal(
                "refraction_strength", "Refraction Strength", LiquidGlassSettings.DEFAULT_REFRACTION_STRENGTH, 0.0f, 1.5f, 0.01f)
                .alias("Liquid Glass Refraction Strength"));
        this.liquidGlassOpacity = listen(liquidGlass.decimal(
                "opacity", "Opacity", LiquidGlassSettings.DEFAULT_OPACITY, 0.0f, 1.0f, 0.01f)
                .alias("Liquid Glass Opacity"));
        this.liquidGlassDarkness = listen(liquidGlass.decimal(
                "darkness", "Darkness", LiquidGlassSettings.DEFAULT_DARKNESS, 0.0f, 0.4f, 0.01f)
                .alias("Liquid Glass Darkness"));
        this.applyLiquidGlassSettings();
    }

    private Value<Number> listen(Value<Number> value) {
        return value.listener((changedValue, previous, current) -> HUD.this.applyLiquidGlassSettings());
    }

    private void applyLiquidGlassSettings() {
        if (this.liquidGlassRadius == null) {
            return;
        }
        LiquidGlassSettings.configure(
                this.floatValue(this.liquidGlassRadius),
                Math.round(this.floatValue(this.liquidGlassBlurIterations)),
                this.floatValue(this.liquidGlassBlurRadius),
                this.floatValue(this.liquidGlassBlurDownscale),
                this.floatValue(this.liquidGlassNoise),
                this.floatValue(this.liquidGlassRefractionPower),
                this.floatValue(this.liquidGlassRefractionStrength),
                this.floatValue(this.liquidGlassOpacity),
                this.floatValue(this.liquidGlassDarkness));
    }

    private float floatValue(Value<Number> setting) {
        return setting.getValue().floatValue();
    }
}
