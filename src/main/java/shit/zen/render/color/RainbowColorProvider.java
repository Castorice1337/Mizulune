package shit.zen.render.color;

import java.awt.Color;
import net.minecraft.util.Mth;

public final class RainbowColorProvider implements ColorProvider {
    private static final float RAINBOW_TEXT_SWEEP_DEGREES = 180.0f;
    private static final float RAINBOW_LIST_SWEEP_DEGREES = 360.0f;
    private final boolean animated;
    private final float speed;
    private final float saturation;
    private final float brightness;
    private final float rowOffset;

    public RainbowColorProvider(boolean animated, float speed, float saturation, float brightness, float rowOffset) {
        this.animated = animated;
        this.speed = speed;
        this.saturation = saturation;
        this.brightness = brightness;
        this.rowOffset = rowOffset;
    }

    @Override
    public int color(ColorContext context) {
        float hueDegrees = (this.animated ? (float)(context.timeMs() % 60000L) * this.speed / 1000.0f : 0.0f)
                + context.rowProgress() * RAINBOW_LIST_SWEEP_DEGREES
                + (float)context.rowIndex() * this.rowOffset
                + context.charProgress() * RAINBOW_TEXT_SWEEP_DEGREES;
        float hue = (hueDegrees % 360.0f) / 360.0f;
        if (hue < 0.0f) {
            hue += 1.0f;
        }
        float sat = Mth.clamp(this.saturation / 100.0f, 0.0f, 1.0f);
        float bright = Mth.clamp(this.brightness / 100.0f, 0.0f, 1.0f);
        return 0xFF000000 | (Color.HSBtoRGB(hue, sat, bright) & 0x00FFFFFF);
    }
}
