package shit.zen.hud;

import net.minecraft.client.KeyMapping;
import net.minecraft.util.Mth;
import shit.zen.event.impl.GlRenderEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.render.DrawContext;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
import shit.zen.render.Paint;
import shit.zen.render.RoundedRectangle;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.utils.render.Argb;
import shit.zen.value.MizuColor;
import shit.zen.value.ToggleValueGroup;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;

public final class KeystrokesModule extends HudElement {

    private static final float DEFAULT_SCALE = 0.90f;

    private static final float KEY_SIZE = 38.0f;
    private static final float KEY_HEIGHT = 38.0f;
    private static final float SPACE_HEIGHT = 24.0f;
    private static final float MOUSE_HEIGHT = 31.0f;
    private static final float GAP = 5.0f;
    private static final float RADIUS = 10.0f;

    private static final float ROW_WIDTH = KEY_SIZE * 3.0f + GAP * 2.0f;
    private static final float MOUSE_WIDTH = (ROW_WIDTH - GAP) * 0.5f;
    private static final float BASE_HEIGHT = KEY_HEIGHT * 2.0f + SPACE_HEIGHT + MOUSE_HEIGHT + GAP * 3.0f;

    private static final double PRESS_SPEED = 0.18;
    private static final double RIPPLE_SPEED = 0.32;

    private static final MizuColor DEFAULT_BACKGROUND_TOP = MizuColor.ofArgb(92, 9, 13, 9);
    private static final MizuColor DEFAULT_BACKGROUND_BOTTOM = MizuColor.ofArgb(126, 4, 7, 4);
    private static final MizuColor DEFAULT_PRESSED = MizuColor.ofArgb(140, 74, 104, 60);
    private static final MizuColor DEFAULT_TEXT = MizuColor.ofArgb(244, 255, 255, 255);

    private final FontRenderer keyFont = FontPresets.poppinsBold(18.0f);
    private final FontRenderer mouseFont = FontPresets.poppinsBold(16.5f);

    private final Paint backgroundPaint = new Paint();
    private final Paint pressedPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint detailPaint = new Paint();
    private final Paint ripplePaint = new Paint();

    private Value<Number> scale;

    private Value<Boolean> showW;
    private Value<Boolean> showA;
    private Value<Boolean> showS;
    private Value<Boolean> showD;
    private Value<Boolean> showSpace;
    private Value<Boolean> showLMB;
    private Value<Boolean> showRMB;

    private Value<Boolean> backgroundEnabled;
    private Value<MizuColor> backgroundTopColor;
    private Value<MizuColor> backgroundBottomColor;
    private Value<MizuColor> pressedColor;

    private Value<Boolean> backgroundBlurEnabled;
    private Value<Number> backgroundBlurRadius;
    private Value<Number> backgroundBlurOpacity;

    private Value<Boolean> shadowEnabled;
    private Value<Number> shadowAlpha;
    private Value<Number> shadowBlur;
    private Value<Number> shadowSpread;
    private Value<Number> shadowOffset;

    private Value<Boolean> rippleEnabled;
    private Value<Boolean> softEdgeEnabled;

    private Value<MizuColor> textColor;

    private final KeyButton keyW;
    private final KeyButton keyA;
    private final KeyButton keyS;
    private final KeyButton keyD;
    private final KeyButton keySpace;
    private final KeyButton keyLMB;
    private final KeyButton keyRMB;

    public KeystrokesModule() {
        super("Keystrokes");

        this.keyW = new KeyButton("W", mc.options.keyUp);
        this.keyA = new KeyButton("A", mc.options.keyLeft);
        this.keyS = new KeyButton("S", mc.options.keyDown);
        this.keyD = new KeyButton("D", mc.options.keyRight);
        this.keySpace = new KeyButton("SPACE", mc.options.keyJump);
        this.keyLMB = new KeyButton("LMB", mc.options.keyAttack);
        this.keyRMB = new KeyButton("RMB", mc.options.keyUse);

        this.setX(100.0f);
        this.setY(100.0f);
        this.setWidth(ROW_WIDTH * DEFAULT_SCALE);
        this.setHeight(BASE_HEIGHT * DEFAULT_SCALE);
        this.setEnabled(true);
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup layout = root.group("layout", "Layout");
        this.scale = layout.decimal("scale", "Scale", DEFAULT_SCALE, 0.55, 1.45, 0.02)
                .alias("Size")
                .alias("整体大小");

        ValueGroup visible = root.group("visible_keys", "Visible Keys");
        this.showW = visible.bool("show_w", "Show W", true).alias("W");
        this.showA = visible.bool("show_a", "Show A", true).alias("A");
        this.showS = visible.bool("show_s", "Show S", true).alias("S");
        this.showD = visible.bool("show_d", "Show D", true).alias("D");
        this.showSpace = visible.bool("show_space", "Show Space", true).alias("Space");
        this.showLMB = visible.bool("show_lmb", "Show LMB", true).alias("LMB");
        this.showRMB = visible.bool("show_rmb", "Show RMB", true).alias("RMB");

        ToggleValueGroup background = root.toggleGroup("background", "Background", true);
        this.backgroundEnabled = background.getEnabledValue().alias("Background");
        this.backgroundTopColor = background.color("top", "Top", DEFAULT_BACKGROUND_TOP);
        this.backgroundBottomColor = background.color("bottom", "Bottom", DEFAULT_BACKGROUND_BOTTOM);
        this.pressedColor = background.color("pressed", "Pressed", DEFAULT_PRESSED);

        ToggleValueGroup backgroundBlur = background.toggleGroup("blur", "Background Blur", true);
        this.backgroundBlurEnabled = backgroundBlur.getEnabledValue().alias("Background Blur");
        this.backgroundBlurRadius = backgroundBlur.decimal("radius", "Blur Radius", 10.0f, 0.0f, 24.0f, 0.5f)
                .alias("Blur Radius");
        this.backgroundBlurOpacity = backgroundBlur.decimal("opacity", "Blur Opacity", 0.62f, 0.0f, 1.0f, 0.01f)
                .alias("Blur Opacity");

        ToggleValueGroup shadow = root.toggleGroup("shadow", "Shadow", true);
        this.shadowEnabled = shadow.getEnabledValue().alias("Shadow");
        this.shadowAlpha = shadow.integer("alpha", "Alpha", 142, 0, 255, 1).alias("Shadow Alpha");
        this.shadowBlur = shadow.decimal("blur", "Blur", 8.0f, 0.0f, 22.0f, 0.25f).alias("Shadow Blur");
        this.shadowSpread = shadow.decimal("spread", "Spread", 2.6f, 0.0f, 8.0f, 0.25f).alias("Shadow Spread");
        this.shadowOffset = shadow.decimal("offset_y", "Offset Y", 3.0f, 0.0f, 10.0f, 0.25f).alias("Shadow Offset Y");

        ValueGroup animation = root.group("animation", "Animation");
        this.rippleEnabled = animation.bool("ripple", "Ripple", true).alias("Ripple");
        this.softEdgeEnabled = animation.bool("soft_edge", "Soft Edge", true).alias("Soft Edge");

        ValueGroup colors = root.group("colors", "Colors");
        this.textColor = colors.color("text", "Text", DEFAULT_TEXT);
    }

    @Override
    public void onRender2D(Render2DEvent event, float x, float y) {
    }

    @Override
    public void onGlRender(GlRenderEvent event, float x, float y) {
        if (mc.player == null) {
            return;
        }

        DrawContext ctx = (DrawContext) event.drawContext();
        Layout layout = this.buildLayout();

        this.setWidth(layout.width * layout.scale);
        this.setHeight(layout.height * layout.scale);

        ctx.save();
        ctx.translate(x, y);
        ctx.scale(layout.scale, layout.scale);

        float cursorY = 0.0f;

        if (this.bool(this.showW, true)) {
            this.drawButton(ctx, this.keyW, (layout.width - KEY_SIZE) * 0.5f, cursorY,
                    KEY_SIZE, KEY_HEIGHT, this.keyFont, false);
            cursorY += KEY_HEIGHT + GAP;
        }

        int movementCount = this.movementCount();
        if (movementCount > 0) {
            float rowWidth = movementCount * KEY_SIZE + (movementCount - 1) * GAP;
            float cursorX = (layout.width - rowWidth) * 0.5f;

            if (this.bool(this.showA, true)) {
                this.drawButton(ctx, this.keyA, cursorX, cursorY, KEY_SIZE, KEY_HEIGHT, this.keyFont, false);
                cursorX += KEY_SIZE + GAP;
            }

            if (this.bool(this.showS, true)) {
                this.drawButton(ctx, this.keyS, cursorX, cursorY, KEY_SIZE, KEY_HEIGHT, this.keyFont, false);
                cursorX += KEY_SIZE + GAP;
            }

            if (this.bool(this.showD, true)) {
                this.drawButton(ctx, this.keyD, cursorX, cursorY, KEY_SIZE, KEY_HEIGHT, this.keyFont, false);
            }

            cursorY += KEY_HEIGHT + GAP;
        }

        if (this.bool(this.showSpace, true)) {
            this.drawButton(ctx, this.keySpace, (layout.width - ROW_WIDTH) * 0.5f, cursorY,
                    ROW_WIDTH, SPACE_HEIGHT, this.keyFont, true);
            cursorY += SPACE_HEIGHT + GAP;
        }

        int mouseCount = this.mouseCount();
        if (mouseCount > 0) {
            float rowWidth = mouseCount * MOUSE_WIDTH + (mouseCount - 1) * GAP;
            float cursorX = (layout.width - rowWidth) * 0.5f;

            if (this.bool(this.showLMB, true)) {
                this.drawButton(ctx, this.keyLMB, cursorX, cursorY, MOUSE_WIDTH, MOUSE_HEIGHT, this.mouseFont, false);
                cursorX += MOUSE_WIDTH + GAP;
            }

            if (this.bool(this.showRMB, true)) {
                this.drawButton(ctx, this.keyRMB, cursorX, cursorY, MOUSE_WIDTH, MOUSE_HEIGHT, this.mouseFont, false);
            }
        }

        ctx.restore();
    }

    private Layout buildLayout() {
        float width = 1.0f;
        float height = 0.0f;
        int rows = 0;

        if (this.bool(this.showW, true)) {
            width = Math.max(width, KEY_SIZE);
            height += KEY_HEIGHT;
            rows++;
        }

        int movementCount = this.movementCount();
        if (movementCount > 0) {
            width = Math.max(width, movementCount * KEY_SIZE + (movementCount - 1) * GAP);
            height += KEY_HEIGHT;
            rows++;
        }

        if (this.bool(this.showSpace, true)) {
            width = Math.max(width, ROW_WIDTH);
            height += SPACE_HEIGHT;
            rows++;
        }

        int mouseCount = this.mouseCount();
        if (mouseCount > 0) {
            width = Math.max(width, mouseCount * MOUSE_WIDTH + (mouseCount - 1) * GAP);
            height += MOUSE_HEIGHT;
            rows++;
        }

        if (rows > 1) {
            height += GAP * (rows - 1);
        }

        if (height <= 0.0f) {
            height = 1.0f;
        }

        return new Layout(this.number(this.scale, DEFAULT_SCALE), width, height);
    }

    private int movementCount() {
        int count = 0;
        if (this.bool(this.showA, true)) count++;
        if (this.bool(this.showS, true)) count++;
        if (this.bool(this.showD, true)) count++;
        return count;
    }

    private int mouseCount() {
        int count = 0;
        if (this.bool(this.showLMB, true)) count++;
        if (this.bool(this.showRMB, true)) count++;
        return count;
    }

    private void drawButton(DrawContext ctx, KeyButton button, float x, float y,
                            float width, float height, FontRenderer font, boolean space) {
        button.update(this.bool(this.rippleEnabled, true));

        float press = button.press();
        float radius = Math.min(RADIUS, Math.min(width, height) * 0.45f);
        RoundedRectangle rect = RoundedRectangle.ofXYWHR(x, y, width, height, radius);

        this.drawSurface(ctx, rect, button, press);

        if (space) {
            this.drawSpaceGlyph(ctx, rect, press);
        } else {
            this.drawTextCentered(ctx, button.label,
                    rect.x1 + rect.getWidth() * 0.5f,
                    rect.y1 + rect.getHeight() * 0.5f,
                    font,
                    press);
        }
    }

    private void drawSurface(DrawContext ctx, RoundedRectangle rect, KeyButton button, float press) {
        if (this.bool(this.shadowEnabled, true)) {
            this.drawCompactShadow(ctx, rect, press);
        }

        if (this.bool(this.backgroundBlurEnabled, true)) {
            ctx.drawBackdropBlurredRoundedRect(
                    rect,
                    this.number(this.backgroundBlurRadius, 10.0f),
                    this.number(this.backgroundBlurOpacity, 0.62f),
                    0x00000000
            );
        }

        if (this.bool(this.backgroundEnabled, true)) {
            this.drawBackgroundSkin(ctx, rect);
        }

        if (this.bool(this.rippleEnabled, true) && button.rippleAlive()) {
            this.drawRipple(ctx, rect, button.ripple());
        }

        this.drawPressedWash(ctx, rect, press);

        if (this.bool(this.softEdgeEnabled, true)) {
            this.drawSoftEdge(ctx, rect, press);
        }
    }

    private void drawCompactShadow(DrawContext ctx, RoundedRectangle rect, float press) {
        int baseAlpha = this.integer(this.shadowAlpha, 142);
        float blur = this.number(this.shadowBlur, 8.0f);
        float spread = Math.max(this.number(this.shadowSpread, 2.6f), Math.min(4.5f, blur * 0.34f));
        float offsetY = this.number(this.shadowOffset, 3.0f);

        int black = 0xFF000000;

        ctx.drawBlurredRoundedRect(
                rect,
                0.0f,
                offsetY,
                blur,
                spread,
                Argb.withAlpha(black, baseAlpha)
        );

        ctx.drawBlurredRoundedRect(
                rect,
                0.0f,
                Math.max(1.0f, offsetY * 0.42f),
                Math.max(2.8f, blur * 0.52f),
                Math.max(0.8f, spread * 0.38f),
                Argb.withAlpha(black, Math.round(baseAlpha * 0.52f))
        );

        if (press > 0.001f) {
            int pressed = this.color(this.pressedColor, DEFAULT_PRESSED).toArgb();
            ctx.drawBlurredRoundedRect(
                    rect,
                    0.0f,
                    0.8f,
                    Math.max(2.0f, blur * 0.60f),
                    Math.max(0.6f, spread * 0.24f),
                    Argb.scaleAlpha(pressed, 0.28f * press)
            );
        }
    }

    private void drawBackgroundSkin(DrawContext ctx, RoundedRectangle rect) {
        int top = this.color(this.backgroundTopColor, DEFAULT_BACKGROUND_TOP).toArgb();
        int bottom = this.color(this.backgroundBottomColor, DEFAULT_BACKGROUND_BOTTOM).toArgb();

        this.backgroundPaint.setColor(0xFFFFFFFF);
        this.backgroundPaint.setStrokeCap(Paint.StrokeCap.FILL);
        this.backgroundPaint.setMaskFilter(null);
        this.backgroundPaint.setLinGradient(null);
        this.backgroundPaint.setShader(null);
        this.backgroundPaint.setGradCoords(new Paint.GradientCoords(
                rect.x1,
                rect.y1,
                rect.x1,
                rect.y2,
                top,
                bottom
        ));

        ctx.drawRoundedRect(rect, this.backgroundPaint);
    }

    private void drawRipple(DrawContext ctx, RoundedRectangle rect, float progress) {
        float t = Mth.clamp(progress, 0.0f, 1.0f);
        float eased = this.easeOutCubic(t);

        float cx = rect.x1 + rect.getWidth() * 0.5f;
        float cy = rect.y1 + rect.getHeight() * 0.5f;
        float radius = Math.max(1.0f, (float) Math.hypot(rect.getWidth(), rect.getHeight()) * 0.62f * eased);

        int pressed = this.color(this.pressedColor, DEFAULT_PRESSED).toArgb();

        ctx.save();
        ctx.clipRoundedRect(rect, true);

        this.ripplePaint.setGradCoords(null);
        this.ripplePaint.setStrokeCap(Paint.StrokeCap.FILL);
        this.ripplePaint.setColor(Argb.scaleAlpha(pressed, 0.82f - t * 0.24f));
        ctx.drawArc(cx - radius, cy - radius, cx + radius, cy + radius, 0.0f, 360.0f, false, this.ripplePaint);

        if (t < 0.96f) {
            this.ripplePaint.setStrokeCap(Paint.StrokeCap.STROKE);
            this.ripplePaint.setStrokeWidth(1.05f);
            this.ripplePaint.setColor(Argb.withAlpha(0xFFFFFFFF, Math.round((1.0f - t) * 44.0f)));
            ctx.drawArc(cx - radius, cy - radius, cx + radius, cy + radius, 0.0f, 360.0f, false, this.ripplePaint);
            this.ripplePaint.setStrokeCap(Paint.StrokeCap.FILL);
        }

        ctx.restore();
    }

    private void drawPressedWash(DrawContext ctx, RoundedRectangle rect, float press) {
        if (press <= 0.001f) {
            return;
        }

        int pressed = this.color(this.pressedColor, DEFAULT_PRESSED).toArgb();

        this.pressedPaint.setGradCoords(null);
        this.pressedPaint.setMaskFilter(null);
        this.pressedPaint.setLinGradient(null);
        this.pressedPaint.setShader(null);
        this.pressedPaint.setStrokeCap(Paint.StrokeCap.FILL);
        this.pressedPaint.setColor(Argb.scaleAlpha(pressed, 0.30f * press));

        ctx.drawRoundedRect(rect, this.pressedPaint);
    }

    private void drawSoftEdge(DrawContext ctx, RoundedRectangle rect, float press) {
        float width = rect.getWidth();
        float height = rect.getHeight();

        ctx.save();
        ctx.clipRoundedRect(rect, true);

        this.detailPaint.setGradCoords(null);
        this.detailPaint.setStrokeCap(Paint.StrokeCap.FILL);

        this.detailPaint.setColor(Argb.withAlpha(0xFFFFFFFF, Math.round(13.0f + press * 10.0f)));
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(
                rect.x1 + RADIUS * 0.82f,
                rect.y1 + 1.0f,
                width - RADIUS * 1.64f,
                0.75f,
                0.38f
        ), this.detailPaint);

        this.detailPaint.setColor(Argb.withAlpha(0xFF000000, Math.round(20.0f + press * 12.0f)));
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(
                rect.x1 + RADIUS * 0.76f,
                rect.y1 + height - 1.65f,
                width - RADIUS * 1.52f,
                0.85f,
                0.42f
        ), this.detailPaint);

        ctx.restore();
    }

    private void drawSpaceGlyph(DrawContext ctx, RoundedRectangle rect, float press) {
        float lineWidth = rect.getWidth() * 0.58f;
        float lineHeight = 2.0f;
        float lineX = rect.x1 + (rect.getWidth() - lineWidth) * 0.5f;
        float lineY = rect.y1 + (rect.getHeight() - lineHeight) * 0.5f;

        this.detailPaint.setGradCoords(null);
        this.detailPaint.setStrokeCap(Paint.StrokeCap.FILL);
        this.detailPaint.setColor(Argb.interpolate(0xDDFFFFFF, 0xFFFFFFFF, press));

        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(
                lineX,
                lineY,
                lineWidth,
                lineHeight,
                lineHeight * 0.5f
        ), this.detailPaint);
    }

    private void drawTextCentered(DrawContext ctx, String text, float centerX, float centerY,
                                  FontRenderer font, float press) {
        float textWidth = GlHelper.getStringWidth(text, font);
        float ascent = GlHelper.getFontAscent(font);
        float capHeight = font.getMetrics().capHeight();

        float drawX = centerX - textWidth * 0.5f;
        float drawY = centerY - capHeight * 0.5f - (ascent - capHeight);

        this.textPaint.setGradCoords(null);
        this.textPaint.setStrokeCap(Paint.StrokeCap.FILL);

        this.textPaint.setColor(Argb.withAlpha(0xFF000000, 70));
        ctx.drawString(text, drawX, drawY + 0.6f, font, this.textPaint);

        this.textPaint.setColor(this.resolveTextColor(press));
        ctx.drawString(text, drawX, drawY, font, this.textPaint);
    }

    private int resolveTextColor(float press) {
        int base = this.color(this.textColor, DEFAULT_TEXT).toArgb();
        return Argb.interpolate(base, 0xFFFFFFFF, press * 0.12f);
    }

    private float easeOutCubic(float value) {
        float t = Mth.clamp(value, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private boolean bool(Value<Boolean> value, boolean fallback) {
        return value == null ? fallback : Boolean.TRUE.equals(value.getValue());
    }

    private float number(Value<Number> value, float fallback) {
        if (value == null || value.getValue() == null) {
            return fallback;
        }
        return value.getValue().floatValue();
    }

    private int integer(Value<Number> value, int fallback) {
        if (value == null || value.getValue() == null) {
            return fallback;
        }
        return value.getValue().intValue();
    }

    private MizuColor color(Value<MizuColor> value, MizuColor fallback) {
        return value != null && value.getValue() != null ? value.getValue() : fallback;
    }

    private record Layout(float scale, float width, float height) {
    }

    private static final class KeyButton {
        private final String label;
        private final KeyMapping binding;

        private final SmoothAnimationTimer pressAnimation = new SmoothAnimationTimer();
        private final SmoothAnimationTimer rippleAnimation = new SmoothAnimationTimer();

        private boolean wasDown;
        private boolean rippleAlive;

        private KeyButton(String label, KeyMapping binding) {
            this.label = label;
            this.binding = binding;
            this.pressAnimation.setCurrentValue(0.0);
            this.pressAnimation.setToValue(0.0);
            this.rippleAnimation.setCurrentValue(1.0);
            this.rippleAnimation.setToValue(1.0);
        }

        private boolean isDown() {
            return this.binding != null && this.binding.isDown();
        }

        private void update(boolean rippleEnabled) {
            boolean down = this.isDown();

            if (!rippleEnabled) {
                this.rippleAlive = false;
                this.rippleAnimation.setCurrentValue(1.0);
                this.rippleAnimation.setToValue(1.0);
            } else if (down && !this.wasDown) {
                this.rippleAnimation.setCurrentValue(0.0);
                this.rippleAnimation.setToValue(0.0);
                this.rippleAlive = true;
            }

            this.pressAnimation.animate(down ? 1.0 : 0.0, PRESS_SPEED, Easings.EASE_OUT_POW3);
            this.pressAnimation.tick();

            if (this.rippleAlive) {
                this.rippleAnimation.animate(1.0, RIPPLE_SPEED, Easings.EASE_OUT_POW4);
                this.rippleAnimation.tick();

                if (this.rippleAnimation.getValueF() >= 0.995f) {
                    this.rippleAlive = false;
                    this.rippleAnimation.setCurrentValue(1.0);
                    this.rippleAnimation.setToValue(1.0);
                }
            }

            this.wasDown = down;
        }

        private float press() {
            return this.pressAnimation.getValueF();
        }

        private float ripple() {
            return this.rippleAnimation.getValueF();
        }

        private boolean rippleAlive() {
            return this.rippleAlive;
        }
    }
}
