package shit.zen.render.backend;

import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.FilterBlurMode;
import org.jetbrains.skia.FilterTileMode;
import org.jetbrains.skia.Font;
import org.jetbrains.skia.Image;
import org.jetbrains.skia.ImageFilter;
import org.jetbrains.skia.MaskFilter;
import org.jetbrains.skia.PaintMode;
import org.jetbrains.skia.Rect;
import org.jetbrains.skia.RRect;
import org.jetbrains.skia.SamplingMode;

final class SkikoEffects {
    private SkikoEffects() {
    }

    static void drawBlurredRRect(Canvas canvas, RRect rrect, float blurRadius, int color) {
        if (canvas == null || rrect == null || (color >>> 24) == 0) {
            return;
        }
        try (org.jetbrains.skia.Paint paint = new org.jetbrains.skia.Paint()) {
            paint.setAntiAlias(true);
            paint.setColor(color);
            paint.setMode(PaintMode.FILL);
            if (blurRadius > 0.001f) {
                paint.setMaskFilter(MaskFilter.Companion.makeBlur(FilterBlurMode.NORMAL, blurRadius, true));
            }
            canvas.drawRRect(rrect, paint);
        }
    }

    static void drawTextGlow(Canvas canvas, String text, float x, float baselineY, Font font, int glowColor, float radius) {
        if (canvas == null || text == null || text.isEmpty() || font == null || radius <= 0.001f || (glowColor >>> 24) == 0) {
            return;
        }
        try (org.jetbrains.skia.Paint paint = new org.jetbrains.skia.Paint()) {
            paint.setAntiAlias(true);
            paint.setColor(glowColor);
            paint.setImageFilter(ImageFilter.Companion.makeBlur(radius, radius, FilterTileMode.DECAL, null, null));
            canvas.drawString(text, x, baselineY, font, paint);
        }
    }

    static void drawSelfBlur(Canvas canvas, Rect bounds, float radius, Runnable painter) {
        if (canvas == null || painter == null) {
            return;
        }
        if (radius <= 0.001f) {
            painter.run();
            return;
        }
        float pad = Math.max(1.0f, radius * 2.0f);
        Rect layerBounds = Rect.makeLTRB(bounds.getLeft() - pad, bounds.getTop() - pad, bounds.getRight() + pad, bounds.getBottom() + pad);
        try (org.jetbrains.skia.Paint paint = new org.jetbrains.skia.Paint()) {
            paint.setAntiAlias(true);
            paint.setImageFilter(ImageFilter.Companion.makeBlur(radius, radius, FilterTileMode.DECAL, null, null));
            canvas.saveLayer(layerBounds, paint);
            try {
                painter.run();
            } finally {
                canvas.restore();
            }
        }
    }

    static void drawBackdropBlurredRect(Canvas canvas, Image snapshot, RRect clip, Rect dst,
                                        float blurRadius, float opacity, int color,
                                        float guiScale, int surfaceWidth, int surfaceHeight) {
        if (canvas == null || snapshot == null || clip == null || dst == null || dst.getWidth() <= 0.0f || dst.getHeight() <= 0.0f) {
            return;
        }
        float clampedOpacity = clamp(opacity, 0.0f, 1.0f);
        if (clampedOpacity <= 0.001f) {
            return;
        }
        float scale = Math.max(1.0f, guiScale);
        float guiWidth = Math.max(1.0f, (float)surfaceWidth / scale);
        float guiHeight = Math.max(1.0f, (float)surfaceHeight / scale);
        float pad = Math.max(1.0f, blurRadius * 2.0f);
        float sampleLeft = clamp(dst.getLeft() - pad, 0.0f, guiWidth);
        float sampleTop = clamp(dst.getTop() - pad, 0.0f, guiHeight);
        float sampleRight = clamp(dst.getRight() + pad, 0.0f, guiWidth);
        float sampleBottom = clamp(dst.getBottom() + pad, 0.0f, guiHeight);
        if (sampleRight <= sampleLeft || sampleBottom <= sampleTop) {
            return;
        }
        Rect src = Rect.makeLTRB(sampleLeft * scale, sampleTop * scale, sampleRight * scale, sampleBottom * scale);
        Rect expandedDst = Rect.makeLTRB(sampleLeft, sampleTop, sampleRight, sampleBottom);
        canvas.save();
        try {
            canvas.clipRRect(clip, true);
            try (org.jetbrains.skia.Paint imagePaint = new org.jetbrains.skia.Paint()) {
                imagePaint.setAntiAlias(true);
                imagePaint.setAlpha(Math.round(clampedOpacity * 255.0f));
                if (blurRadius > 0.001f) {
                    imagePaint.setImageFilter(ImageFilter.Companion.makeBlur(blurRadius, blurRadius, FilterTileMode.CLAMP, null, null));
                }
                canvas.drawImageRect(snapshot, src, expandedDst, SamplingMode.Companion.getLINEAR(), imagePaint, true);
            }
            int overlayAlpha = Math.round((float)(color >>> 24) * clampedOpacity);
            if (color != 0 && overlayAlpha > 0) {
                try (org.jetbrains.skia.Paint overlayPaint = new org.jetbrains.skia.Paint()) {
                    overlayPaint.setAntiAlias(true);
                    overlayPaint.setMode(PaintMode.FILL);
                    overlayPaint.setColor((overlayAlpha << 24) | (color & 0x00FFFFFF));
                    canvas.drawRRect(clip, overlayPaint);
                }
            }
        } finally {
            canvas.restore();
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
