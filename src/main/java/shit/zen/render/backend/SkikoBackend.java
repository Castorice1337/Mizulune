package shit.zen.render.backend;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.skia.BackendRenderTarget;
import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.Color4f;
import org.jetbrains.skia.Data;
import org.jetbrains.skia.DirectContext;
import org.jetbrains.skia.FilterTileMode;
import org.jetbrains.skia.Font;
import org.jetbrains.skia.FontMgr;
import org.jetbrains.skia.FontStyle;
import org.jetbrains.skia.FramebufferFormat;
import org.jetbrains.skia.Gradient;
import org.jetbrains.skia.Image;
import org.jetbrains.skia.PaintMode;
import org.jetbrains.skia.PathBuilder;
import org.jetbrains.skia.PathDirection;
import org.jetbrains.skia.Rect;
import org.jetbrains.skia.RRect;
import org.jetbrains.skia.Shader;
import org.jetbrains.skia.Surface;
import org.jetbrains.skia.SurfaceColorFormat;
import org.jetbrains.skia.SurfaceOrigin;
import org.jetbrains.skia.Typeface;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import shit.zen.render.DrawContext;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlyphMetrics;
import shit.zen.render.Paint;
import shit.zen.render.Path;
import shit.zen.render.Rectangle;
import shit.zen.render.RoundedRectangle;
import shit.zen.render.Texture;
import shit.zen.utils.misc.Assets;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class SkikoBackend implements RenderBackend {
    private final Map<String, Typeface> typefaceCache = new HashMap<>();
    private final Map<String, Font> fontCache = new HashMap<>();
    private final Map<String, Image> imageCache = new HashMap<>();
    private DirectContext directContext;
    private BackendRenderTarget renderTarget;
    private Surface surface;
    private Canvas canvas;
    private int currentFbo = -1;
    private int currentWidth = -1;
    private int currentHeight = -1;
    private int beginDepth = 0;
    private boolean active;

    @Override
    public BackendType type() {
        return BackendType.SKIKO;
    }

    @Override
    public boolean handles2D() {
        return true;
    }

    @Override
    public String debugSummary() {
        return "fbo=" + this.currentFbo
                + " surface=" + this.currentWidth + "x" + this.currentHeight
                + " active=" + this.active;
    }

    @Override
    public void begin(GuiGraphics guiGraphics, PoseStack poseStack) {
        RenderSystem.assertOnRenderThread();
        this.ensureContext();
        Minecraft mc = Minecraft.getInstance();
        int fbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int width = Math.max(1, mc.getWindow().getWidth());
        int height = Math.max(1, mc.getWindow().getHeight());
        this.ensureSurface(fbo, width, height);
        this.canvas = this.surface.getCanvas();
        this.canvas.save();
        this.beginDepth = 1;
        float guiScale = (float) mc.getWindow().getGuiScale();
        if (guiScale <= 0.0f) {
            guiScale = 1.0f;
        }
        this.canvas.scale(guiScale, guiScale);
        this.active = true;
    }

    @Override
    public void end() {
        if (!this.active) {
            return;
        }
        while (this.beginDepth > 0) {
            this.canvas.restore();
            this.beginDepth--;
        }
        this.flush();
        this.active = false;
        this.canvas = null;
    }

    @Override
    public void flush() {
        if (!this.active || this.surface == null || this.directContext == null) {
            return;
        }
        this.surface.flushAndSubmit();
        this.directContext.flush(this.surface);
    }

    @Override
    public void afterExternalGlDraw() {
        if (this.directContext != null) {
            this.directContext.resetGLAll();
        }
    }

    @Override
    public void save(DrawContext drawContext) {
        this.requireCanvas().save();
        this.beginDepth++;
    }

    @Override
    public void restore(DrawContext drawContext) {
        if (this.beginDepth > 1) {
            this.requireCanvas().restore();
            this.beginDepth--;
        }
    }

    @Override
    public void translate(float x, float y) {
        this.requireCanvas().translate(x, y);
    }

    @Override
    public void scale(float scaleX, float scaleY) {
        this.requireCanvas().scale(scaleX, scaleY);
    }

    @Override
    public void rotate(float degrees) {
        this.requireCanvas().rotate(degrees);
    }

    @Override
    public void clipRect(DrawContext drawContext, Rectangle rectangle) {
        this.requireCanvas().clipRect(Rect.makeXYWH(rectangle.getX(), rectangle.getY(), rectangle.getWidth(), rectangle.getHeight()), true);
    }

    @Override
    public void clipRoundedRect(DrawContext drawContext, RoundedRectangle roundedRectangle) {
        this.requireCanvas().clipRRect(this.toRRect(roundedRectangle), true);
    }

    @Override
    public void clearClipStack() {
        if (this.canvas == null) {
            return;
        }
        while (this.beginDepth > 1) {
            this.requireCanvas().restore();
            this.beginDepth--;
        }
    }

    @Override
    public void drawRect(DrawContext drawContext, float x, float y, float width, float height, Paint paint) {
        try (org.jetbrains.skia.Paint skPaint = this.toSkPaint(paint)) {
            this.requireCanvas().drawRect(Rect.makeXYWH(x, y, width, height), skPaint);
        }
    }

    @Override
    public void drawRoundedRect(DrawContext drawContext, RoundedRectangle roundedRectangle, Paint paint) {
        try (org.jetbrains.skia.Paint skPaint = this.toSkPaint(paint)) {
            this.requireCanvas().drawRRect(this.toRRect(roundedRectangle), skPaint);
        }
    }

    @Override
    public void drawLine(DrawContext drawContext, float x1, float y1, float x2, float y2, Paint paint) {
        try (org.jetbrains.skia.Paint skPaint = this.toSkPaint(paint)) {
            skPaint.setMode(PaintMode.STROKE);
            skPaint.setStrokeWidth(Math.max(0.5f, paint.getStrokeWidth()));
            this.requireCanvas().drawLine(x1, y1, x2, y2, skPaint);
        }
    }

    @Override
    public void drawString(DrawContext drawContext, String text, float x, float y, FontRenderer fontRenderer, Paint paint) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try (org.jetbrains.skia.Paint skPaint = this.toSkPaint(paint)) {
            Font font = this.getSkFont(fontRenderer);
            GlyphMetrics glyphMetrics = fontRenderer.getMetrics();
            float baselineY = this.toSkiaBaseline(y + glyphMetrics.ascent() - 1.0f, font);
            this.drawFormattedString(text, x, baselineY, glyphMetrics.height(), fontRenderer, font, skPaint, paint.getColor());
        }
    }

    @Override
    public void drawArc(DrawContext drawContext, float x1, float y1, float x2, float y2, float startAngle, float sweepAngle, Paint paint) {
        try (org.jetbrains.skia.Paint skPaint = this.toSkPaint(paint)) {
            if (paint.getCapStyle() == Paint.StrokeCap.STROKE) {
                skPaint.setMode(PaintMode.STROKE);
                skPaint.setStrokeWidth(Math.max(0.5f, paint.getStrokeWidth()));
            }
            this.requireCanvas().drawArc(x1, y1, x2, y2, startAngle, sweepAngle, paint.getCapStyle() != Paint.StrokeCap.STROKE, skPaint);
        }
    }

    @Override
    public void drawPath(DrawContext drawContext, Path path, Paint paint) {
        if (path == null) {
            return;
        }
        try (org.jetbrains.skia.Path skPath = this.toSkPath(path);
             org.jetbrains.skia.Paint skPaint = this.toSkPaint(paint)) {
            this.requireCanvas().drawPath(skPath, skPaint);
        }
    }

    @Override
    public void drawTexture(DrawContext drawContext, Texture texture, Rectangle srcRect, Rectangle dstRect, Paint paint) {
        Image image = this.getImage(texture);
        if (image == null) {
            return;
        }
        try (org.jetbrains.skia.Paint skPaint = this.toSkPaint(paint)) {
            this.requireCanvas().drawImageRect(image,
                    Rect.makeXYWH(srcRect.getX(), srcRect.getY(), srcRect.getWidth(), srcRect.getHeight()),
                    Rect.makeXYWH(dstRect.getX(), dstRect.getY(), dstRect.getWidth(), dstRect.getHeight()),
                    skPaint, true);
        }
    }

    @Override
    public void drawBlurredRoundedRect(DrawContext drawContext, RoundedRectangle roundedRectangle,
                                       float offsetX, float offsetY, float blurRadius, float spread, int color) {
        RoundedRectangle expanded = RoundedRectangle.ofXYWHRadii(
                roundedRectangle.x1 + offsetX - spread,
                roundedRectangle.y1 + offsetY - spread,
                roundedRectangle.getWidth() + spread * 2.0f,
                roundedRectangle.getHeight() + spread * 2.0f,
                new float[]{
                        Math.max(0.0f, roundedRectangle.topLeftRadius + spread),
                        Math.max(0.0f, roundedRectangle.topRightRadius + spread),
                        Math.max(0.0f, roundedRectangle.bottomRightRadius + spread),
                        Math.max(0.0f, roundedRectangle.bottomLeftRadius + spread)
                });
        Paint shadowPaint = new Paint().setColor(color).setMaskFilter(new Paint.BlurMaskFilter(Math.max(0.01f, blurRadius)));
        try (org.jetbrains.skia.Paint skPaint = this.toSkPaint(shadowPaint)) {
            this.requireCanvas().drawRRect(this.toRRect(expanded), skPaint);
        }
    }

    @Override
    public void drawBlur(DrawContext drawContext, float x, float y, float width, float height, float radius, Runnable runnable) {
        if (radius <= 0.001f) {
            runnable.run();
            return;
        }
        this.save(drawContext);
        try {
            runnable.run();
        } finally {
            this.restore(drawContext);
        }
    }

    private void ensureContext() {
        if (this.directContext == null) {
            this.directContext = DirectContext.Companion.makeGL();
        }
    }

    private void ensureSurface(int fbo, int width, int height) {
        if (this.surface != null && fbo == this.currentFbo && width == this.currentWidth && height == this.currentHeight) {
            return;
        }
        this.closeSurface();
        this.renderTarget = BackendRenderTarget.Companion.makeGL(width, height, 0, 8, fbo, FramebufferFormat.GR_GL_RGBA8);
        this.surface = Surface.Companion.makeFromBackendRenderTarget(this.directContext, this.renderTarget,
                SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888, null, null);
        if (this.surface == null) {
            throw new IllegalStateException("Skiko surface creation failed");
        }
        this.currentFbo = fbo;
        this.currentWidth = width;
        this.currentHeight = height;
    }

    private void closeSurface() {
        if (this.surface != null) {
            this.surface.close();
            this.surface = null;
        }
        if (this.renderTarget != null) {
            this.renderTarget.close();
            this.renderTarget = null;
        }
    }

    private Canvas requireCanvas() {
        if (this.canvas == null) {
            throw new IllegalStateException("Skiko canvas is not active");
        }
        return this.canvas;
    }

    private org.jetbrains.skia.Paint toSkPaint(Paint paint) {
        org.jetbrains.skia.Paint skPaint = new org.jetbrains.skia.Paint();
        skPaint.setAntiAlias(paint.isAntialias());
        skPaint.setColor(paint.getColor());
        skPaint.setMode(paint.getCapStyle() == Paint.StrokeCap.STROKE ? PaintMode.STROKE : PaintMode.FILL);
        skPaint.setStrokeWidth(Math.max(0.5f, paint.getStrokeWidth()));
        if (paint.getGradCoords() != null) {
            Paint.GradientCoords grad = paint.getGradCoords();
            skPaint.setShader(Shader.Companion.makeLinearGradient(
                    grad.x1, grad.y1, grad.x2, grad.y2,
                    new Gradient(new Gradient.Colors(
                            new Color4f[]{new Color4f(grad.color1), new Color4f(grad.color2)},
                            new float[]{0.0f, 1.0f},
                            FilterTileMode.CLAMP,
                            null),
                            new Gradient.Interpolation()),
                    null));
        }
        if (paint.getBlurRadius() > 0.001f) {
            skPaint.setMaskFilter(org.jetbrains.skia.MaskFilter.Companion.makeBlur(org.jetbrains.skia.FilterBlurMode.NORMAL, paint.getBlurRadius(), true));
        }
        return skPaint;
    }

    private RRect toRRect(RoundedRectangle roundedRectangle) {
        Rect rect = Rect.makeLTRB(roundedRectangle.x1, roundedRectangle.y1, roundedRectangle.x2, roundedRectangle.y2);
        return RRect.makeComplexLTRB(rect.getLeft(), rect.getTop(), rect.getRight(), rect.getBottom(),
                new float[]{
                        roundedRectangle.topLeftRadius, roundedRectangle.topLeftRadius,
                        roundedRectangle.topRightRadius, roundedRectangle.topRightRadius,
                        roundedRectangle.bottomRightRadius, roundedRectangle.bottomRightRadius,
                        roundedRectangle.bottomLeftRadius, roundedRectangle.bottomLeftRadius
                });
    }

    private org.jetbrains.skia.Path toSkPath(Path path) {
        try (PathBuilder builder = new PathBuilder()) {
            for (Path.PathSegment segment : path.getSegments()) {
                switch (segment.type) {
                    case MOVE_TO -> builder.moveTo(segment.coords[0], segment.coords[1]);
                    case LINE_TO -> builder.lineTo(segment.coords[0], segment.coords[1]);
                    case QUAD_TO -> builder.quadTo(segment.coords[0], segment.coords[1], segment.coords[2], segment.coords[3]);
                    case CUBIC_TO -> builder.cubicTo(segment.coords[0], segment.coords[1], segment.coords[2], segment.coords[3], segment.coords[4], segment.coords[5]);
                    case CLOSE -> builder.closePath();
                    case RECT -> builder.addRect(Rect.makeXYWH(segment.rect.getX(), segment.rect.getY(), segment.rect.getWidth(), segment.rect.getHeight()), PathDirection.CLOCKWISE, 0);
                    case RRECT -> builder.addRRect(this.toRRect(segment.roundedRect), PathDirection.CLOCKWISE, 0);
                }
            }
            return builder.detach();
        }
    }

    private Font getSkFont(FontRenderer fontRenderer) {
        float skiaSize = this.getSkiaFontSize(fontRenderer);
        String key = fontRenderer.getFontName() + "-" + skiaSize;
        return this.fontCache.computeIfAbsent(key, ignored -> {
            Typeface typeface = this.getTypeface(fontRenderer.getFontName());
            Font font = new Font(typeface, skiaSize);
            font.setSubpixel(true);
            return font;
        });
    }

    private float getSkiaFontSize(FontRenderer fontRenderer) {
        // The legacy atlas path halves FontRenderer.size in Fonts.getCustomFont(...)
        // before applying GUI-scale atlas oversampling. Keep Skia in the same GUI
        // coordinate size so existing HUD layout math remains valid.
        return Math.max(1.0f, fontRenderer.getSize() * 0.5f);
    }

    private float toSkiaBaseline(float legacyAtlasTopY, Font font) {
        return legacyAtlasTopY - font.getMetrics().getAscent();
    }

    private Typeface getTypeface(String fontName) {
        return this.typefaceCache.computeIfAbsent(fontName, ignored -> {
            FontMgr fontMgr = FontMgr.Companion.getDefault();
            try (InputStream stream = Assets.open("/assets/zen/fonts/" + fontName)) {
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

    private Image getImage(Texture texture) {
        if (texture == null || texture.getResourceLocation() == null) {
            return null;
        }
        String key = texture.getResourceLocation().toString();
        return this.imageCache.computeIfAbsent(key, ignored -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                try (InputStream stream = mc.getResourceManager().open(texture.getResourceLocation())) {
                    return Image.Companion.makeFromEncoded(stream.readAllBytes());
                }
            } catch (Exception ignoredException) {
                return null;
            }
        });
    }

    private void drawFormattedString(String text, float x, float baselineY, float lineHeight,
                                     FontRenderer fontRenderer, Font font, org.jetbrains.skia.Paint skPaint, int baseColor) {
        Canvas currentCanvas = this.requireCanvas();
        StringBuilder segment = new StringBuilder();
        float penX = x;
        float penY = baselineY;
        int currentColor = baseColor;
        int alpha = baseColor & 0xFF000000;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                penX = this.drawTextSegment(currentCanvas, segment, penX, penY, fontRenderer, font, skPaint, currentColor);
                segment.setLength(0);
                penX = x;
                penY += Math.max(1.0f, lineHeight);
                continue;
            }
            if (ch == '\u00a7' && i + 1 < text.length()) {
                penX = this.drawTextSegment(currentCanvas, segment, penX, penY, fontRenderer, font, skPaint, currentColor);
                segment.setLength(0);
                char code = Character.toUpperCase(text.charAt(++i));
                Integer rgb = this.getMinecraftTextColor(code);
                if (rgb != null) {
                    currentColor = alpha | rgb;
                } else if (code == 'R') {
                    currentColor = baseColor;
                }
                continue;
            }
            segment.append(ch);
        }
        this.drawTextSegment(currentCanvas, segment, penX, penY, fontRenderer, font, skPaint, currentColor);
    }

    private float drawTextSegment(Canvas currentCanvas, StringBuilder segment, float x, float y,
                                  FontRenderer fontRenderer, Font font, org.jetbrains.skia.Paint skPaint, int color) {
        if (segment.length() == 0) {
            return x;
        }
        String value = segment.toString();
        float advance = this.measureLegacyTextWidth(fontRenderer, value);
        float skiaWidth = Math.max(0.001f, font.measureTextWidth(value, skPaint));
        float scaleX = Math.max(0.25f, Math.min(4.0f, advance / skiaWidth));
        skPaint.setColor(color);
        if (Math.abs(scaleX - 1.0f) <= 0.01f) {
            currentCanvas.drawString(value, x, y, font, skPaint);
        } else {
            currentCanvas.save();
            try {
                currentCanvas.translate(x, y);
                currentCanvas.scale(scaleX, 1.0f);
                currentCanvas.drawString(value, 0.0f, 0.0f, font, skPaint);
            } finally {
                currentCanvas.restore();
            }
        }
        return x + advance;
    }

    private float measureLegacyTextWidth(FontRenderer fontRenderer, String value) {
        this.flush();
        try {
            return fontRenderer.getWidth(value);
        } finally {
            this.afterExternalGlDraw();
        }
    }

    private Integer getMinecraftTextColor(char code) {
        return switch (code) {
            case '0' -> 0x000000;
            case '1' -> 0x0000AA;
            case '2' -> 0x00AA00;
            case '3' -> 0x00AAAA;
            case '4' -> 0xAA0000;
            case '5' -> 0xAA00AA;
            case '6' -> 0xFFAA00;
            case '7' -> 0xAAAAAA;
            case '8' -> 0x555555;
            case '9' -> 0x5555FF;
            case 'A' -> 0x55FF55;
            case 'B' -> 0x55FFFF;
            case 'C' -> 0xFF5555;
            case 'D' -> 0xFF55FF;
            case 'E' -> 0xFFFF55;
            case 'F' -> 0xFFFFFF;
            default -> null;
        };
    }
}
