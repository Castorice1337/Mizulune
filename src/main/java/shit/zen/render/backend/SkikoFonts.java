package shit.zen.render.backend;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.Data;
import org.jetbrains.skia.Font;
import org.jetbrains.skia.FontMgr;
import org.jetbrains.skia.FontStyle;
import org.jetbrains.skia.Typeface;
import shit.zen.render.CustomFont;
import shit.zen.utils.misc.Assets;

final class SkikoFonts {
    private final Map<String, Typeface> typefaceCache = new HashMap<>();
    private final Map<String, Font> fontCache = new HashMap<>();

    boolean canDraw(CustomFont customFont) {
        return customFont != null
                && customFont.getFontResourceName() != null
                && !customFont.getFontResourceName().isEmpty();
    }

    boolean draw(Canvas canvas, CustomFont customFont, String text, float x, float y,
                 float baseR, float baseG, float baseB, float alpha,
                 boolean rainbow, int rainbowOffset) {
        if (canvas == null || !this.canDraw(customFont) || text == null || text.isEmpty()) {
            return text == null || text.isEmpty();
        }
        Font font = this.getFont(customFont);
        if (font == null) {
            return false;
        }
        int baseColor = toArgb(baseR, baseG, baseB, alpha);
        int baseAlpha = baseColor & 0xFF000000;
        float startX = roundLegacy(x);
        float topY = roundLegacy(y - 1.0f);
        float ascent = customFont.getFontMetrics().getAscent() / (float)Math.max(1, customFont.getScale());
        float penX = startX;
        float penTopY = topY;
        int lineStart = 0;
        int currentColor = baseColor;
        StringBuilder segment = new StringBuilder(text.length());
        try (org.jetbrains.skia.Paint paint = new org.jetbrains.skia.Paint()) {
            paint.setAntiAlias(true);
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '\n') {
                    penX = this.drawTextSegment(canvas, segment, penX, penTopY + ascent, font, paint, currentColor, customFont.getLetterSpacing());
                    segment.setLength(0);
                    penX = startX;
                    penTopY += Math.max(1.0f, customFont.getStringHeight(text.substring(lineStart, i)));
                    lineStart = i + 1;
                    continue;
                }
                if (ch == '\u00a7' && i + 1 < text.length()) {
                    penX = this.drawTextSegment(canvas, segment, penX, penTopY + ascent, font, paint, currentColor, customFont.getLetterSpacing());
                    segment.setLength(0);
                    char code = Character.toUpperCase(text.charAt(++i));
                    Integer rgb = CustomFont.getMinecraftColorCode(code);
                    if (rgb != null) {
                        currentColor = baseAlpha | rgb;
                    } else if (code == 'R') {
                        currentColor = baseColor;
                    }
                    continue;
                }
                segment.append(ch);
            }
            this.drawTextSegment(canvas, segment, penX, penTopY + ascent, font, paint, currentColor, customFont.getLetterSpacing());
        }
        return true;
    }

    private float drawTextSegment(Canvas canvas, StringBuilder segment, float x, float baselineY, Font font,
                                  org.jetbrains.skia.Paint paint, int color, float letterSpacing) {
        if (segment.length() == 0) {
            return x;
        }
        String value = segment.toString();
        paint.setColor(color);
        canvas.drawString(value, x, baselineY, font, paint);
        float spacing = Math.max(0.0f, letterSpacing) * Math.max(0, value.length() - 1);
        return x + Math.max(0.0f, font.measureTextWidth(value, paint)) + spacing;
    }

    private Font getFont(CustomFont customFont) {
        String resourceName = customFont.getFontResourceName();
        float size = Math.max(1.0f, customFont.getFontSize());
        String key = resourceName + "-" + size;
        return this.fontCache.computeIfAbsent(key, ignored -> {
            Typeface typeface = this.getTypeface(resourceName);
            if (typeface == null) {
                return null;
            }
            Font font = new Font(typeface, size);
            font.setSubpixel(true);
            font.setLinearMetrics(false);
            font.setBaselineSnapped(false);
            return font;
        });
    }

    private Typeface getTypeface(String resourceName) {
        return this.typefaceCache.computeIfAbsent(resourceName, ignored -> {
            FontMgr fontMgr = FontMgr.Companion.getDefault();
            try (InputStream stream = Assets.open("/assets/mizulune/fonts/" + resourceName)) {
                if (stream == null) {
                    return this.getFallbackTypeface(fontMgr);
                }
                byte[] bytes = stream.readAllBytes();
                Typeface typeface = fontMgr.makeFromData(Data.Companion.makeFromBytes(bytes, 0, bytes.length), 0);
                return typeface != null ? typeface : this.getFallbackTypeface(fontMgr);
            } catch (Exception exception) {
                return this.getFallbackTypeface(fontMgr);
            }
        });
    }

    private Typeface getFallbackTypeface(FontMgr fontMgr) {
        Typeface typeface = fontMgr.matchFamilyStyle("Arial", FontStyle.Companion.getNORMAL());
        return typeface != null ? typeface : Typeface.Companion.makeEmpty();
    }

    private static int toArgb(float r, float g, float b, float a) {
        return normalizeComponent(a) << 24
                | normalizeComponent(r) << 16
                | normalizeComponent(g) << 8
                | normalizeComponent(b);
    }

    private static int normalizeComponent(float value) {
        float scaled = value <= 1.0f ? value * 255.0f : value;
        return Math.max(0, Math.min(255, Math.round(scaled)));
    }

    private static float roundLegacy(float value) {
        return (float)Math.round(value * 10.0f) / 10.0f;
    }
}
