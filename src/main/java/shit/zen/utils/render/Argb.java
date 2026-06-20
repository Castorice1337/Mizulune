package shit.zen.utils.render;

import java.awt.Color;
import lombok.Generated;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class Argb {
    private static final String UTILITY_CLASS_MSG = "This is a utility class and cannot be instantiated";

    public static int fromArgbComponents(int alpha, int red, int green, int blue) {
        return clampByte(alpha) << 24 | clampByte(red) << 16 | clampByte(green) << 8 | clampByte(blue);
    }

    public static int fromRgbaComponents(int red, int green, int blue, int alpha) {
        return fromArgbComponents(alpha, red, green, blue);
    }

    public static int fromRgb(int red, int green, int blue) {
        return fromArgbComponents(255, red, green, blue);
    }

    public static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | clampByte(alpha) << 24;
    }

    public static int withAlpha(int color, float alpha) {
        return withAlpha(color, Math.round(Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f));
    }

    public static int scaleAlpha(int color, float alphaScale) {
        return withAlpha(color, Math.round(alpha(color) * Mth.clamp(alphaScale, 0.0f, 1.0f)));
    }

    public static int interpolate(int colorA, int colorB, float progress) {
        float t = Mth.clamp(progress, 0.0f, 1.0f);
        float inverse = 1.0f - t;
        return fromArgbComponents(
                Math.round(alpha(colorA) * inverse + alpha(colorB) * t),
                Math.round(red(colorA) * inverse + red(colorB) * t),
                Math.round(green(colorA) * inverse + green(colorB) * t),
                Math.round(blue(colorA) * inverse + blue(colorB) * t));
    }

    public static int animate(int colorA, int colorB, double progress) {
        if (progress > 1.0) {
            progress = 1.0 - progress % 1.0;
        }
        return interpolate(colorA, colorB, (float)progress);
    }

    public static int animateOffset(int colorA, int colorB, long offsetMs) {
        return animate(colorA, colorB, (double)((System.currentTimeMillis() + offsetMs) % 4000L) / 2000.0);
    }

    public static int rainbow(int speed, int offset) {
        int safeSpeed = Math.max(1, speed);
        int hueDegrees = (int)((System.currentTimeMillis() / (long)safeSpeed + (long)offset) % 360L);
        float hue = (float)hueDegrees / 360.0f;
        return 0xFF000000 | Color.HSBtoRGB(hue, 0.5f, 1.0f) & 0x00FFFFFF;
    }

    public static int playerColor(Player player) {
        int hash = player.getName().getString().hashCode();
        return fromRgb(hash >> 16 & 0xFF, hash >> 8 & 0xFF, hash & 0xFF);
    }

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int red(int color) {
        return color >> 16 & 0xFF;
    }

    public static int green(int color) {
        return color >> 8 & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    private static int clampByte(int value) {
        return Mth.clamp(value, 0, 255);
    }

    @Generated
    private Argb() {
        throw new UnsupportedOperationException(UTILITY_CLASS_MSG);
    }
}
