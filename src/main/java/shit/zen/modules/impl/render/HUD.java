package shit.zen.modules.impl.render;

import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.render.LiquidGlassSettings;
import shit.zen.settings.impl.NumberSetting;

public class HUD extends Module {
    public final NumberSetting liquidGlassRadius = new NumberSetting(
            "Liquid Glass Radius", LiquidGlassSettings.DEFAULT_RADIUS, 1.0f, 8.0f, 0.01f) {
        @Override
        public void onChanged(Number previous, Number current) {
            HUD.this.applyLiquidGlassSettings();
        }
    };
    public final NumberSetting liquidGlassBlurIterations = new NumberSetting(
            "Liquid Glass Blur Iterations", LiquidGlassSettings.DEFAULT_BLUR_ITERATIONS, 0, 4, 1) {
        @Override
        public void onChanged(Number previous, Number current) {
            HUD.this.applyLiquidGlassSettings();
        }
    };
    public final NumberSetting liquidGlassBlurRadius = new NumberSetting(
            "Liquid Glass Blur Radius", LiquidGlassSettings.DEFAULT_BLUR_RADIUS, 0.0f, 24.0f, 0.01f) {
        @Override
        public void onChanged(Number previous, Number current) {
            HUD.this.applyLiquidGlassSettings();
        }
    };
    public final NumberSetting liquidGlassBlurDownscale = new NumberSetting(
            "Liquid Glass Blur Downscale", LiquidGlassSettings.DEFAULT_BLUR_DOWNSCALE, 0.25f, 2.0f, 0.05f) {
        @Override
        public void onChanged(Number previous, Number current) {
            HUD.this.applyLiquidGlassSettings();
        }
    };
    public final NumberSetting liquidGlassNoise = new NumberSetting(
            "Liquid Glass Noise", LiquidGlassSettings.DEFAULT_NOISE, 0.0f, 0.15f, 0.001f) {
        @Override
        public void onChanged(Number previous, Number current) {
            HUD.this.applyLiquidGlassSettings();
        }
    };
    public final NumberSetting liquidGlassRefractionPower = new NumberSetting(
            "Liquid Glass Refraction Power", LiquidGlassSettings.DEFAULT_REFRACTION_POWER, 0.1f, 6.0f, 0.01f) {
        @Override
        public void onChanged(Number previous, Number current) {
            HUD.this.applyLiquidGlassSettings();
        }
    };
    public final NumberSetting liquidGlassRefractionStrength = new NumberSetting(
            "Liquid Glass Refraction Strength", LiquidGlassSettings.DEFAULT_REFRACTION_STRENGTH, 0.0f, 1.5f, 0.01f) {
        @Override
        public void onChanged(Number previous, Number current) {
            HUD.this.applyLiquidGlassSettings();
        }
    };
    public final NumberSetting liquidGlassOpacity = new NumberSetting(
            "Liquid Glass Opacity", LiquidGlassSettings.DEFAULT_OPACITY, 0.0f, 1.0f, 0.01f) {
        @Override
        public void onChanged(Number previous, Number current) {
            HUD.this.applyLiquidGlassSettings();
        }
    };
    public final NumberSetting liquidGlassDarkness = new NumberSetting(
            "Liquid Glass Darkness", LiquidGlassSettings.DEFAULT_DARKNESS, 0.0f, 0.4f, 0.01f) {
        @Override
        public void onChanged(Number previous, Number current) {
            HUD.this.applyLiquidGlassSettings();
        }
    };

    public HUD() {
        super("HUD", Category.RENDER);
        this.applyLiquidGlassSettings();
    }

    private void applyLiquidGlassSettings() {
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

    private float floatValue(NumberSetting setting) {
        return setting.getValue().floatValue();
    }
}
