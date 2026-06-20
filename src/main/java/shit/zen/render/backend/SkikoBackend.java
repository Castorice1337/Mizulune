package shit.zen.render.backend;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
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
import org.jetbrains.skia.Matrix33;
import org.jetbrains.skia.PaintMode;
import org.jetbrains.skia.PathBuilder;
import org.jetbrains.skia.PathDirection;
import org.jetbrains.skia.Rect;
import org.jetbrains.skia.RRect;
import org.jetbrains.skia.SamplingMode;
import org.jetbrains.skia.Shader;
import org.jetbrains.skia.Surface;
import org.jetbrains.skia.SurfaceColorFormat;
import org.jetbrains.skia.SurfaceOrigin;
import org.jetbrains.skia.Typeface;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import shit.zen.render.CustomFont;
import shit.zen.render.DrawContext;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlyphMetrics;
import shit.zen.render.LiquidGlassStyle;
import shit.zen.render.Paint;
import shit.zen.render.Path;
import shit.zen.render.Rectangle;
import shit.zen.render.RoundedRectangle;
import shit.zen.render.Texture;
import shit.zen.utils.misc.Assets;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public final class SkikoBackend implements RenderBackend {
    private final Map<String, Typeface> typefaceCache = new HashMap<>();
    private final Map<String, Font> fontCache = new HashMap<>();
    private final SkikoTextures textures = new SkikoTextures();
    private final SkikoFonts customFonts = new SkikoFonts();
    private final SkikoLiquidGlass liquidGlass = new SkikoLiquidGlass();
    private DirectContext directContext;
    private BackendRenderTarget renderTarget;
    private Surface surface;
    private Image backdropSnapshot;
    private Canvas canvas;
    private GlStateGuard frameState;
    private int currentFbo = -1;
    private int currentWidth = -1;
    private int currentHeight = -1;
    private int backdropSnapshotWidth = -1;
    private int backdropSnapshotHeight = -1;
    private int backdropBlurCount;
    private int backdropBlurMissCount;
    private float guiScale = 1.0f;
    private int beginDepth = 0;
    private int externalGlDepth = 0;
    private int externalGlVao;
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
                + " active=" + this.active
                + " images=" + this.textures.getCachedImageCount()
                + " imageMiss=" + this.textures.getMissingResourceCount()
                + " backdrop=" + (this.backdropSnapshot != null)
                + " blur=" + this.backdropBlurCount
                + " blurMiss=" + this.backdropBlurMissCount
                + " " + this.liquidGlass.debugSummary();
    }

    @Override
    public void begin(GuiGraphics guiGraphics, PoseStack poseStack) {
        RenderSystem.assertOnRenderThread();
        this.frameState = GlStateGuard.capture();
        int fbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        this.ensureContext();
        this.directContext.resetGLAll();
        Minecraft mc = Minecraft.getInstance();
        int width = Math.max(1, mc.getWindow().getWidth());
        int height = Math.max(1, mc.getWindow().getHeight());
        this.ensureSurface(fbo, width, height);
        this.captureBackdropSnapshot(width, height);
        this.canvas = this.surface.getCanvas();
        this.canvas.save();
        this.beginDepth = 1;
        this.externalGlDepth = 0;
        this.guiScale = (float) mc.getWindow().getGuiScale();
        if (this.guiScale <= 0.0f) {
            this.guiScale = 1.0f;
        }
        this.canvas.scale(this.guiScale, this.guiScale);
        this.active = true;
    }

    @Override
    public void end() {
        if (!this.active) {
            this.closeBackdropSnapshot();
            this.restoreFrameState();
            this.canvas = null;
            return;
        }
        try {
            while (this.beginDepth > 0) {
                this.canvas.restore();
                this.beginDepth--;
            }
            this.flush();
        } finally {
            this.closeBackdropSnapshot();
            this.restoreFrameState();
            this.active = false;
            this.canvas = null;
            this.externalGlDepth = 0;
        }
    }

    @Override
    public void flush() {
        if (!this.active || this.surface == null || this.directContext == null) {
            return;
        }
        this.directContext.flushAndSubmit(this.surface, true);
    }

    @Override
    public void beforeExternalGlDraw() {
        if (this.externalGlDepth++ > 0) {
            return;
        }
        this.flush();
        this.restoreCapturedState();
        BufferUploader.reset();
        this.ensureExternalGlVaoBound();
    }

    @Override
    public void afterExternalGlDraw() {
        if (this.externalGlDepth <= 0) {
            return;
        }
        if (--this.externalGlDepth > 0) {
            return;
        }
        BufferUploader.reset();
        if (this.directContext != null) {
            this.directContext.resetGLAll();
        }
    }

    @Override
    public void pushExternalPose(PoseStack poseStack) {
        Canvas currentCanvas = this.requireCanvas();
        currentCanvas.save();
        if (poseStack != null) {
            currentCanvas.concat(this.toSkiaMatrix(poseStack.last().pose()));
        }
    }

    @Override
    public void popExternalPose() {
        this.requireCanvas().restore();
    }

    private void restoreFrameState() {
        this.restoreCapturedState();
        this.frameState = null;
        if (this.directContext != null) {
            this.directContext.resetGLAll();
        }
    }

    private void restoreCapturedState() {
        if (this.frameState != null) {
            this.frameState.restore();
        }
    }

    private void ensureExternalGlVaoBound() {
        if (GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) != 0) {
            return;
        }
        if (this.externalGlVao == 0) {
            this.externalGlVao = GL30.glGenVertexArrays();
        }
        GL30.glBindVertexArray(this.externalGlVao);
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
            float baselineY = this.toHudTextBaseline(y, glyphMetrics);
            this.drawFormattedString(text, this.roundLayout(x), baselineY, Math.max(1.0f, glyphMetrics.height()), font, skPaint, paint.getColor());
        }
    }

    @Override
    public boolean drawCustomFontText(DrawContext drawContext, CustomFont customFont, String text, PoseStack poseStack,
                                      float x, float y, float baseR, float baseG, float baseB, float alpha,
                                      boolean rainbow, int rainbowOffset) {
        if (!this.active || !this.customFonts.canDraw(customFont)) {
            return false;
        }
        Canvas currentCanvas = this.requireCanvas();
        currentCanvas.save();
        try {
            if (poseStack != null) {
                currentCanvas.concat(this.toSkiaMatrix(poseStack.last().pose()));
            }
            return this.customFonts.draw(currentCanvas, customFont, text, x, y, baseR, baseG, baseB, alpha, rainbow, rainbowOffset);
        } finally {
            currentCanvas.restore();
        }
    }

    @Override
    public float drawGlowText(DrawContext drawContext, String text, float x, float y, FontRenderer fontRenderer, int color, int glowColor, float radius) {
        if (text == null || text.isEmpty() || fontRenderer == null) {
            return x;
        }
        Font font = this.getSkFont(fontRenderer);
        GlyphMetrics glyphMetrics = fontRenderer.getMetrics();
        float baselineY = this.toHudTextBaseline(y, glyphMetrics);
        float roundedX = this.roundLayout(x);
        try (org.jetbrains.skia.Paint glowPaint = this.toSkPaint(new Paint().setColor(glowColor))) {
            if (radius > 0.001f && (glowColor >>> 24) != 0) {
                glowPaint.setImageFilter(org.jetbrains.skia.ImageFilter.Companion.makeBlur(radius, radius, FilterTileMode.DECAL, null, null));
                this.drawFormattedString(text, roundedX, baselineY, Math.max(1.0f, glyphMetrics.height()), font, glowPaint, glowColor);
            }
        }
        try (org.jetbrains.skia.Paint textPaint = this.toSkPaint(new Paint().setColor(color))) {
            this.drawFormattedString(text, roundedX, baselineY, Math.max(1.0f, glyphMetrics.height()), font, textPaint, color);
        }
        return roundedX + this.measureFormattedTextWidth(text, font);
    }

    @Override
    public float measureTextWidth(String text, FontRenderer fontRenderer) {
        if (text == null || text.isEmpty() || fontRenderer == null) {
            return 0.0f;
        }
        return this.measureFormattedTextWidth(text, this.getSkFont(fontRenderer));
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
                    this.toImageSourceRect(image, srcRect),
                    Rect.makeXYWH(dstRect.getX(), dstRect.getY(), dstRect.getWidth(), dstRect.getHeight()),
                    SamplingMode.Companion.getLINEAR(), skPaint, true);
        }
    }

    @Override
    public boolean canDrawResourceTexture(ResourceLocation resourceLocation) {
        return this.textures.getResourceImage(resourceLocation) != null;
    }

    @Override
    public boolean drawPlayerHead(DrawContext drawContext, ResourceLocation skinTexture, float x, float y, float width, float height, float alpha, float radius) {
        Image image = this.textures.getResourceImage(skinTexture);
        if (image == null) {
            return false;
        }
        Canvas currentCanvas = this.requireCanvas();
        RRect clip = this.toRRect(RoundedRectangle.ofXYWHR(x, y, width, height, radius));
        Rect dst = Rect.makeXYWH(x, y, width, height);
        int clampedAlpha = (int)Math.max(0.0f, Math.min(255.0f, alpha * 255.0f));
        try (org.jetbrains.skia.Paint paint = new org.jetbrains.skia.Paint()) {
            paint.setAntiAlias(true);
            paint.setColor(clampedAlpha << 24 | 0xFFFFFF);
            currentCanvas.save();
            try {
                currentCanvas.clipRRect(clip, true);
                this.drawSkinLayer(currentCanvas, image, dst, paint, 8.0f, 8.0f, 8.0f, 8.0f);
                this.drawSkinLayer(currentCanvas, image, dst, paint, 40.0f, 8.0f, 8.0f, 8.0f);
            } finally {
                currentCanvas.restore();
            }
        }
        return true;
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
        SkikoEffects.drawBlurredRRect(this.requireCanvas(), this.toRRect(expanded), Math.max(0.01f, blurRadius), color);
    }

    @Override
    public boolean drawBackdropBlurredRect(DrawContext drawContext, float x, float y, float width, float height,
                                           float radius, float blurRadius, float opacity, int color) {
        return this.drawBackdropBlurredRoundedRect(drawContext, RoundedRectangle.ofXYWHR(x, y, width, height, radius),
                blurRadius, opacity, color);
    }

    @Override
    public boolean drawBackdropBlurredRoundedRect(DrawContext drawContext, RoundedRectangle roundedRectangle,
                                                  float blurRadius, float opacity, int color) {
        if (this.backdropSnapshot == null || this.backdropSnapshotWidth <= 0 || this.backdropSnapshotHeight <= 0) {
            this.backdropBlurMissCount++;
            return false;
        }
        this.backdropBlurCount++;
        SkikoEffects.drawBackdropBlurredRect(this.requireCanvas(), this.backdropSnapshot,
                this.toRRect(roundedRectangle),
                Rect.makeXYWH(roundedRectangle.x1, roundedRectangle.y1,
                        roundedRectangle.getWidth(), roundedRectangle.getHeight()),
                Math.max(0.0f, blurRadius), opacity, color,
                this.guiScale, this.backdropSnapshotWidth, this.backdropSnapshotHeight);
        return true;
    }

    @Override
    public boolean drawBackdropBlurredPath(DrawContext drawContext, Path clipPath, Rectangle bounds,
                                           float blurRadius, float opacity, int color) {
        if (this.backdropSnapshot == null || this.backdropSnapshotWidth <= 0 || this.backdropSnapshotHeight <= 0) {
            this.backdropBlurMissCount++;
            return false;
        }
        this.backdropBlurCount++;
        try (org.jetbrains.skia.Path skPath = this.toSkPath(clipPath)) {
            SkikoEffects.drawBackdropBlurredPath(this.requireCanvas(), this.backdropSnapshot, skPath,
                    Rect.makeXYWH(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()),
                    Math.max(0.0f, blurRadius), opacity, color,
                    this.guiScale, this.backdropSnapshotWidth, this.backdropSnapshotHeight);
        }
        return true;
    }

    @Override
    public boolean drawLiquidGlassPanel(DrawContext drawContext, RoundedRectangle roundedRectangle, LiquidGlassStyle style) {
        if (this.backdropSnapshot == null || this.backdropSnapshotWidth <= 0 || this.backdropSnapshotHeight <= 0) {
            this.backdropBlurMissCount++;
            return false;
        }
        LiquidGlassStyle effectiveStyle = style == null ? LiquidGlassStyle.defaultClear() : style;
        RRect clip = this.toRRect(roundedRectangle);
        Rect dst = Rect.makeXYWH(roundedRectangle.x1, roundedRectangle.y1,
                roundedRectangle.getWidth(), roundedRectangle.getHeight());
        if (this.liquidGlass.draw(this.requireCanvas(), this.backdropSnapshot, clip, dst, effectiveStyle,
                this.guiScale, this.backdropSnapshotWidth, this.backdropSnapshotHeight)) {
            return true;
        }
        SkikoEffects.drawBackdropBlurredRect(this.requireCanvas(), this.backdropSnapshot, clip, dst,
                Math.max(0.0f, effectiveStyle.getBlurRadius()), effectiveStyle.getOpacity(),
                effectiveStyle.getTintColor(), this.guiScale, this.backdropSnapshotWidth, this.backdropSnapshotHeight);
        return true;
    }

    @Override
    public void drawBlur(DrawContext drawContext, float x, float y, float width, float height, float radius, Runnable runnable) {
        SkikoEffects.drawSelfBlur(this.requireCanvas(), Rect.makeXYWH(x, y, width, height), radius, runnable);
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

    private void captureBackdropSnapshot(int width, int height) {
        this.closeBackdropSnapshot();
        if (this.surface == null) {
            this.backdropBlurMissCount++;
            return;
        }
        GL11.glFlush();
        this.backdropSnapshot = this.surface.makeImageSnapshot();
        this.backdropSnapshotWidth = width;
        this.backdropSnapshotHeight = height;
        if (this.backdropSnapshot == null) {
            this.backdropSnapshotWidth = -1;
            this.backdropSnapshotHeight = -1;
            this.backdropBlurMissCount++;
        }
    }

    private void closeBackdropSnapshot() {
        if (this.backdropSnapshot != null) {
            this.backdropSnapshot.close();
            this.backdropSnapshot = null;
        }
        this.backdropSnapshotWidth = -1;
        this.backdropSnapshotHeight = -1;
    }

    private void closeSurface() {
        this.closeBackdropSnapshot();
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
        float maxRadius = Math.max(0.0f, Math.min(Math.abs(roundedRectangle.getWidth()), Math.abs(roundedRectangle.getHeight())) * 0.5f);
        return RRect.makeComplexLTRB(rect.getLeft(), rect.getTop(), rect.getRight(), rect.getBottom(),
                new float[]{
                        this.clampRadius(roundedRectangle.topLeftRadius, maxRadius), this.clampRadius(roundedRectangle.topLeftRadius, maxRadius),
                        this.clampRadius(roundedRectangle.topRightRadius, maxRadius), this.clampRadius(roundedRectangle.topRightRadius, maxRadius),
                        this.clampRadius(roundedRectangle.bottomRightRadius, maxRadius), this.clampRadius(roundedRectangle.bottomRightRadius, maxRadius),
                        this.clampRadius(roundedRectangle.bottomLeftRadius, maxRadius), this.clampRadius(roundedRectangle.bottomLeftRadius, maxRadius)
                });
    }

    private float clampRadius(float radius, float maxRadius) {
        return Math.max(0.0f, Math.min(radius, maxRadius));
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
            this.configureFont(font);
            return font;
        });
    }

    private Matrix33 toSkiaMatrix(org.joml.Matrix4f matrix) {
        return new Matrix33(
                matrix.m00(), matrix.m10(), matrix.m30(),
                matrix.m01(), matrix.m11(), matrix.m31(),
                0.0f, 0.0f, 1.0f);
    }

    private void configureFont(Font font) {
        if (font != null) {
            font.setSubpixel(true);
            font.setLinearMetrics(false);
            font.setBaselineSnapped(false);
        }
    }

    private float getSkiaFontSize(FontRenderer fontRenderer) {
        // The atlas path halves FontRenderer.size in Fonts.getCustomFont(...)
        // before applying GUI-scale atlas oversampling. Keep Skia in the same
        // GUI coordinate size so existing HUD layout math remains valid.
        return Math.max(1.0f, fontRenderer.getSize() * 0.5f);
    }

    private float toHudTextBaseline(float y, GlyphMetrics glyphMetrics) {
        return this.roundLayout(y - glyphMetrics.ascent() * 0.2f - 1.0f);
    }

    private float roundLayout(float value) {
        return (float)Math.round(value * 10.0f) / 10.0f;
    }

    private String stripFormatting(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            result.append(ch);
        }
        return result.toString();
    }

    private Typeface getTypeface(String fontName) {
        return this.typefaceCache.computeIfAbsent(fontName, ignored -> {
            FontMgr fontMgr = FontMgr.Companion.getDefault();
            try (InputStream stream = Assets.open("/assets/mizulune/fonts/" + fontName)) {
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
        return this.textures.getResourceImage(texture.getResourceLocation());
    }

    private Rect toImageSourceRect(Image image, Rectangle srcRect) {
        if (srcRect == null || srcRect.getWidth() <= 0.0f || srcRect.getHeight() <= 0.0f) {
            return Rect.makeXYWH(0.0f, 0.0f, image.getWidth(), image.getHeight());
        }
        return Rect.makeXYWH(srcRect.getX(), srcRect.getY(), srcRect.getWidth(), srcRect.getHeight());
    }

    private void drawSkinLayer(Canvas canvas, Image image, Rect dst, org.jetbrains.skia.Paint paint, float u, float v, float width, float height) {
        float scaleX = image.getWidth() / 64.0f;
        float scaleY = image.getHeight() / 64.0f;
        Rect src = Rect.makeXYWH(u * scaleX, v * scaleY, width * scaleX, height * scaleY);
        canvas.drawImageRect(image, src, dst, SamplingMode.Companion.getLINEAR(), paint, true);
    }

    private void drawFormattedString(String text, float x, float baselineY, float lineHeight,
                                     Font font, org.jetbrains.skia.Paint skPaint, int baseColor) {
        Canvas currentCanvas = this.requireCanvas();
        StringBuilder segment = new StringBuilder();
        float penX = x;
        float penY = baselineY;
        int currentColor = baseColor;
        int alpha = baseColor & 0xFF000000;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                penX = this.drawTextSegment(currentCanvas, segment, penX, penY, font, skPaint, currentColor);
                segment.setLength(0);
                penX = x;
                penY += Math.max(1.0f, lineHeight);
                continue;
            }
            if (ch == '\u00a7' && i + 1 < text.length()) {
                penX = this.drawTextSegment(currentCanvas, segment, penX, penY, font, skPaint, currentColor);
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
        this.drawTextSegment(currentCanvas, segment, penX, penY, font, skPaint, currentColor);
    }

    private float measureFormattedTextWidth(String text, Font font) {
        String stripped = this.stripFormatting(text);
        float lineWidth = 0.0f;
        float maxWidth = 0.0f;
        int lineStart = 0;
        for (int i = 0; i <= stripped.length(); i++) {
            if (i == stripped.length() || stripped.charAt(i) == '\n') {
                if (i > lineStart) {
                    lineWidth += Math.max(0.0f, font.measureTextWidth(stripped.substring(lineStart, i)));
                }
                maxWidth = Math.max(maxWidth, lineWidth);
                lineWidth = 0.0f;
                lineStart = i + 1;
            }
        }
        return maxWidth;
    }

    private float drawTextSegment(Canvas currentCanvas, StringBuilder segment, float x, float y,
                                  Font font, org.jetbrains.skia.Paint skPaint, int color) {
        if (segment.length() == 0) {
            return x;
        }
        String value = segment.toString();
        skPaint.setColor(color);
        currentCanvas.drawString(value, x, y, font, skPaint);
        return x + Math.max(0.0f, font.measureTextWidth(value, skPaint));
    }

    private static final class GlStateGuard {
        private final int vertexArray;
        private final int arrayBuffer;
        private final int elementArrayBuffer;
        private final int currentProgram;
        private final int activeTexture;
        private final int texture2d;
        private final int readFramebuffer;
        private final int drawFramebuffer;
        private final int[] viewport = new int[4];
        private final int[] scissorBox = new int[4];
        private final boolean[] colorMask = new boolean[4];
        private final boolean blend;
        private final boolean depthTest;
        private final boolean cullFace;
        private final boolean scissorTest;
        private final boolean depthMask;
        private final int blendSrcRgb;
        private final int blendDstRgb;
        private final int blendSrcAlpha;
        private final int blendDstAlpha;
        private final int blendEquationRgb;
        private final int blendEquationAlpha;

        private GlStateGuard() {
            this.vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            this.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            this.elementArrayBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
            this.currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            this.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            this.texture2d = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            this.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            this.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, this.viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, this.scissorBox);
            this.getBooleanVector(GL11.GL_COLOR_WRITEMASK, this.colorMask);
            this.blend = GL11.glIsEnabled(GL11.GL_BLEND);
            this.depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            this.cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            this.scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            this.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            this.blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            this.blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            this.blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            this.blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            this.blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
            this.blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        }

        private static GlStateGuard capture() {
            return new GlStateGuard();
        }

        private void restore() {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.readFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.drawFramebuffer);
            GL30.glBindVertexArray(this.vertexArray);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.arrayBuffer);
            if (this.vertexArray != 0) {
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.elementArrayBuffer);
            }
            GL20.glUseProgram(this.currentProgram);
            GL13.glActiveTexture(this.activeTexture);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture2d);
            GL11.glViewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
            GL11.glScissor(this.scissorBox[0], this.scissorBox[1], this.scissorBox[2], this.scissorBox[3]);
            GL14.glBlendFuncSeparate(this.blendSrcRgb, this.blendDstRgb, this.blendSrcAlpha, this.blendDstAlpha);
            GL20.glBlendEquationSeparate(this.blendEquationRgb, this.blendEquationAlpha);
            GL11.glColorMask(this.colorMask[0], this.colorMask[1], this.colorMask[2], this.colorMask[3]);
            GL11.glDepthMask(this.depthMask);
            this.setEnabled(GL11.GL_BLEND, this.blend);
            this.setEnabled(GL11.GL_DEPTH_TEST, this.depthTest);
            this.setEnabled(GL11.GL_CULL_FACE, this.cullFace);
            this.setEnabled(GL11.GL_SCISSOR_TEST, this.scissorTest);
        }

        private void getBooleanVector(int pname, boolean[] target) {
            ByteBuffer buffer = BufferUtils.createByteBuffer(target.length);
            GL11.glGetBooleanv(pname, buffer);
            for (int i = 0; i < target.length; i++) {
                target[i] = buffer.get(i) != 0;
            }
        }

        private void setEnabled(int cap, boolean enabled) {
            if (enabled) {
                GL11.glEnable(cap);
            } else {
                GL11.glDisable(cap);
            }
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
