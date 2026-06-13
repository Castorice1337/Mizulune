package shit.zen.value;

import java.awt.Color;
import java.util.Locale;
import net.minecraft.util.Mth;

public final class MizuColor {
    private final int argb;

    private MizuColor(int argb) {
        this.argb = argb;
    }

    public static MizuColor ofArgb(int argb) {
        return new MizuColor(argb);
    }

    public static MizuColor ofArgb(int alpha, int red, int green, int blue) {
        return new MizuColor((clampByte(alpha) << 24) | (clampByte(red) << 16) | (clampByte(green) << 8) | clampByte(blue));
    }

    public static MizuColor ofRgba(int red, int green, int blue, int alpha) {
        return ofArgb(alpha, red, green, blue);
    }

    public static MizuColor ofRgb(int red, int green, int blue) {
        return ofArgb(255, red, green, blue);
    }

    public static MizuColor ofHsb(float hue, float saturation, float brightness, int alpha) {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF;
        return ofArgb(clampByte(alpha) << 24 | rgb);
    }

    public static MizuColor fromHex(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Color hex cannot be null");
        }
        String hex = raw.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        if (hex.length() == 6) {
            return ofArgb(0xFF000000 | Integer.parseUnsignedInt(hex, 16));
        }
        if (hex.length() == 8) {
            return ofArgb((int)Long.parseLong(hex, 16));
        }
        throw new IllegalArgumentException("Unsupported color hex: " + raw);
    }

    public int toArgb() {
        return this.argb;
    }

    public int toRgba() {
        return (this.red() << 24) | (this.green() << 16) | (this.blue() << 8) | this.alpha();
    }

    public int alpha() {
        return this.argb >>> 24;
    }

    public int red() {
        return this.argb >> 16 & 0xFF;
    }

    public int green() {
        return this.argb >> 8 & 0xFF;
    }

    public int blue() {
        return this.argb & 0xFF;
    }

    public MizuColor withAlpha(int alpha) {
        return ofArgb(alpha, this.red(), this.green(), this.blue());
    }

    public MizuColor fullAlpha() {
        return this.withAlpha(255);
    }

    public MizuColor interpolateTo(MizuColor other, float progress) {
        float t = Mth.clamp(progress, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return ofArgb(
                Math.round(this.alpha() * inv + other.alpha() * t),
                Math.round(this.red() * inv + other.red() * t),
                Math.round(this.green() * inv + other.green() * t),
                Math.round(this.blue() * inv + other.blue() * t));
    }

    public float[] toHsb() {
        return Color.RGBtoHSB(this.red(), this.green(), this.blue(), null);
    }

    public String toHexArgb() {
        return String.format(Locale.ROOT, "#%08X", this.argb);
    }

    public String toHexRgba() {
        return String.format(Locale.ROOT, "#%08X", this.toRgba());
    }

    private static int clampByte(int value) {
        return Mth.clamp(value, 0, 255);
    }

    @Override
    public String toString() {
        return this.toHexArgb();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MizuColor other && this.argb == other.argb;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(this.argb);
    }
}
