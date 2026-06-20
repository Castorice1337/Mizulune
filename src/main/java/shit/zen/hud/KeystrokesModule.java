package shit.zen.hud;

import net.minecraft.client.KeyMapping;
import net.minecraft.util.Mth;
import shit.zen.event.impl.GlRenderEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.render.DrawContext;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
import shit.zen.render.LiquidGlassStyle;
import shit.zen.render.Paint;
import shit.zen.render.RoundedRectangle;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.utils.render.Argb;
import shit.zen.value.MizuColor;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;

public final class KeystrokesModule extends HudElement {

    private static final float DEFAULT_SCALE = 0.92f;

    private static final float KEY_SIZE = 38.0f;
    private static final float KEY_HEIGHT = 38.0f;
    private static final float SPACE_HEIGHT = 24.0f;
    private static final float MOUSE_HEIGHT = 31.0f;
    private static final float GAP = 5.0f;
    private static final float RADIUS = 10.5f;

    private static final float ROW_WIDTH = KEY_SIZE * 3.0f + GAP * 2.0f;
    private static final float MOUSE_WIDTH = (ROW_WIDTH - GAP) * 0.5f;
    private static final float BASE_HEIGHT = KEY_HEIGHT * 2.0f + SPACE_HEIGHT + MOUSE_HEIGHT + GAP * 3.0f;

    private static final float PRESS_SPEED = 0.20f;
    private static final float RIPPLE_SPEED = 0.30f;

    private static final MizuColor DEFAULT_GLASS_TOP = MizuColor.ofArgb(42, 12, 14, 12);
    private static final MizuColor DEFAULT_GLASS_BOTTOM = MizuColor.ofArgb(66, 4, 6, 5);
    private static final MizuColor DEFAULT_PRESSED = MizuColor.ofArgb(96, 84, 120, 72);
    private static final MizuColor DEFAULT_TEXT = MizuColor.ofArgb(238, 255, 255, 255);

    private static final LiquidGlassStyle KEY_GLASS_STYLE = LiquidGlassStyle.builder()
            .power(3.4f)
            .refractionPower(1.10f)
            .refractionStrength(0.24f)
            .noise(0.006f)
            .glow(0.06f, 0.012f)
            .glowEdges(0.0f, 0.92f)
            .blurIterations(3)
            .blurRadius(15.0f)
            .blurDownscale(0.62f)
            .opacity(0.74f)
            .tint(0x66040604, 0.36f)
            .chromaStrength(0.0f)
            .darkness(0.30f)
            .build();

    private final FontRenderer keyFont = FontPresets.poppinsBold(19.0f);
    private final FontRenderer mouseFont = FontPresets.poppinsBold(17.0f);

    private final Paint fillPaint = new Paint();
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

    private Value<Boolean> glass;
    private Value<Boolean> shadow;
    private Value<Boolean> ripple;
    private Value<Boolean> softEdge;

    private Value<MizuColor> glassTopColor;
    private Value<MizuColor> glassBottomColor;
    private Value<MizuColor> pressedColor;
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
        this.scale = layout.decimal("scale", "Scale", DEFAULT_SCALE, 0.55, 1.65, 0.02)
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

        ValueGroup visual = root.group("visual", "Visual");
        this.glass = visual.bool("glass", "Glass", true).alias("Frosted Glass");
        this.shadow = visual.bool("shadow", "Shadow", true);
        this.ripple = visual.bool("ripple", "Ripple", true);
        this.softEdge = visual.bool("soft_edge", "Soft Edge", true);

        ValueGroup colors = root.group("colors", "Colors");
        this.glassTopColor = colors.color("glass_top", "Glass Top", DEFAULT_GLASS_TOP);
        this.glassBottomColor = colors.color("glass_bottom", "Glass Bottom", DEFAULT_GLASS_BOTTOM);
        this.pressedColor = colors.color("pressed", "Pressed", DEFAULT_PRESSED);
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
        Layout layout = buildLayout();

        this.setWidth(layout.width * layout.scale);
        this.setHeight(layout.height * layout.scale);

        ctx.save();
        ctx.translate(x, y);
        ctx.scale(layout.scale, layout.scale);

        float cursorY = 0.0f;

        if (bool(this.showW, true)) {
            drawButton(ctx, this.keyW, (layout.width - KEY_SIZE) * 0.5f, cursorY, KEY_SIZE, KEY_HEIGHT, this.keyFont, false);
            cursorY += KEY_HEIGHT + GAP;
        }

        int movementCount = movementCount();
        if (movementCount > 0) {
            float rowWidth = movementCount * KEY_SIZE + (movementCount - 1) * GAP;
            float cursorX = (layout.width - rowWidth) * 0.5f;

            if (bool(this.showA, true)) {
                drawButton(ctx, this.keyA, cursorX, cursorY, KEY_SIZE, KEY_HEIGHT, this.keyFont, false);
                cursorX += KEY_SIZE + GAP;
            }

            if (bool(this.showS, true)) {
                drawButton(ctx, this.keyS, cursorX, cursorY, KEY_SIZE, KEY_HEIGHT, this.keyFont, false);
                cursorX += KEY_SIZE + GAP;
            }

            if (bool(this.showD, true)) {
                drawButton(ctx, this.keyD, cursorX, cursorY, KEY_SIZE, KEY_HEIGHT, this.keyFont, false);
            }

            cursorY += KEY_HEIGHT + GAP;
        }

        if (bool(this.showSpace, true)) {
            drawButton(ctx, this.keySpace, (layout.width - ROW_WIDTH) * 0.5f, cursorY, ROW_WIDTH, SPACE_HEIGHT, this.keyFont, true);
            cursorY += SPACE_HEIGHT + GAP;
        }

        int mouseCount = mouseCount();
        if (mouseCount > 0) {
            float rowWidth = mouseCount * MOUSE_WIDTH + (mouseCount - 1) * GAP;
            float cursorX = (layout.width - rowWidth) * 0.5f;

            if (bool(this.showLMB, true)) {
                drawButton(ctx, this.keyLMB, cursorX, cursorY, MOUSE_WIDTH, MOUSE_HEIGHT, this.mouseFont, false);
                cursorX += MOUSE_WIDTH + GAP;
            }

            if (bool(this.showRMB, true)) {
                drawButton(ctx, this.keyRMB, cursorX, cursorY, MOUSE_WIDTH, MOUSE_HEIGHT, this.mouseFont, false);
            }
        }

        ctx.restore();
    }

    private Layout buildLayout() {
        float width = 1.0f;
        float height = 0.0f;
        int rows = 0;

        if (bool(this.showW, true)) {
            width = Math.max(width, KEY_SIZE);
            height += KEY_HEIGHT;
            rows++;
        }

        int movementCount = movementCount();
        if (movementCount > 0) {
            width = Math.max(width, movementCount * KEY_SIZE + (movementCount - 1) * GAP);
            height += KEY_HEIGHT;
            rows++;
        }

        if (bool(this.showSpace, true)) {
            width = Math.max(width, ROW_WIDTH);
            height += SPACE_HEIGHT;
            rows++;
        }

        int mouseCount = mouseCount();
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

        return new Layout(number(this.scale, DEFAULT_SCALE), width, height);
    }

    private int movementCount() {
        int count = 0;
        if (bool(this.showA, true)) count++;
        if (bool(this.showS, true)) count++;
        if (bool(this.showD, true)) count++;
        return count;
    }

    private int mouseCount() {
        int count = 0;
        if (bool(this.showLMB, true)) count++;
        if (bool(this.showRMB, true)) count++;
        return count;
    }

    private void drawButton(DrawContext ctx, KeyButton button, float x, float y, float width, float height, FontRenderer font, boolean space) {
        button.update(bool(this.ripple, true));

        float press = button.press();
        RoundedRectangle rect = RoundedRectangle.ofXYWHR(x, y, width, height, Math.min(RADIUS, Math.min(width, height) * 0.36f));

        drawSurface(ctx, rect, button, press);

        if (space) {
            drawSpaceGlyph(ctx, rect, press);
        } else {
            drawTextCentered(ctx, button.label, rect.x1 + rect.getWidth() * 0.5f, rect.y1 + rect.getHeight() * 0.5f, font, press);
        }
    }

    private void drawSurface(DrawContext ctx, RoundedRectangle rect, KeyButton button, float press) {
        if (bool(this.shadow, true)) {
            drawNaturalShadow(ctx, rect, press);
        }

        if (bool(this.glass, true)) {
            ctx.drawLiquidGlassPanel(rect, KEY_GLASS_STYLE);
        }

        drawTransparentSkin(ctx, rect);

        if (bool(this.ripple, true) && button.rippleAlive()) {
            drawRipple(ctx, rect, button.ripple());
        }

        drawPressedWash(ctx, rect, press);

        if (bool(this.softEdge, true)) {
            drawSoftSpecularEdge(ctx, rect, press);
        }
    }

    private void drawNaturalShadow(DrawContext ctx, RoundedRectangle rect, float press) {
        int black = 0xFF000000;
        int pressed = color(this.pressedColor, DEFAULT_PRESSED).toArgb();

        ctx.drawBlurredRoundedRect(rect, 0.0f, 4.5f, 15.0f, 0.0f, Argb.withAlpha(black, 62));
        ctx.drawBlurredRoundedRect(rect, 0.0f, 2.0f, 7.5f, 0.0f, Argb.withAlpha(black, 46));

        if (press > 0.001f) {
            ctx.drawBlurredRoundedRect(rect, 0.0f, 0.0f, 9.5f, 0.0f, Argb.scaleAlpha(pressed, press * 0.24f));
        }
    }

    private void drawTransparentSkin(DrawContext ctx, RoundedRectangle rect) {
        int top = color(this.glassTopColor, DEFAULT_GLASS_TOP).toArgb();
        int bottom = color(this.glassBottomColor, DEFAULT_GLASS_BOTTOM).toArgb();

        fillPaint.setStrokeCap(Paint.StrokeCap.FILL);
        fillPaint.setGradCoords(new Paint.GradientCoords(
                rect.x1,
                rect.y1,
                rect.x1,
                rect.y2,
                top,
                bottom
        ));

        ctx.drawRoundedRect(rect, fillPaint);
    }

    private void drawPressedWash(DrawContext ctx, RoundedRectangle rect, float press) {
        if (press <= 0.001f) {
            return;
        }

        int pressed = color(this.pressedColor, DEFAULT_PRESSED).toArgb();

        fillPaint.setGradCoords(null);
        fillPaint.setStrokeCap(Paint.StrokeCap.FILL);
        fillPaint.setColor(Argb.scaleAlpha(pressed, 0.34f * press));

        ctx.drawRoundedRect(rect, fillPaint);
    }

    private void drawRipple(DrawContext ctx, RoundedRectangle rect, float progress) {
        float t = Mth.clamp(progress, 0.0f, 1.0f);
        float eased = easeOutCubic(t);

        float cx = rect.x1 + rect.getWidth() * 0.5f;
        float cy = rect.y1 + rect.getHeight() * 0.5f;
        float radius = Math.max(1.0f, (float) Math.hypot(rect.getWidth(), rect.getHeight()) * 0.62f * eased);

        int pressed = color(this.pressedColor, DEFAULT_PRESSED).toArgb();
        int fill = Argb.scaleAlpha(pressed, 0.66f - t * 0.20f);

        ctx.save();
        ctx.clipRoundedRect(rect, true);

        ripplePaint.setGradCoords(null);
        ripplePaint.setStrokeCap(Paint.StrokeCap.FILL);
        ripplePaint.setColor(fill);
        ctx.drawArc(cx - radius, cy - radius, cx + radius, cy + radius, 0.0f, 360.0f, false, ripplePaint);

        if (t < 0.96f) {
            ripplePaint.setStrokeCap(Paint.StrokeCap.STROKE);
            ripplePaint.setStrokeWidth(1.15f);
            ripplePaint.setColor(Argb.withAlpha(0xFFFFFFFF, Math.round((1.0f - t) * 52.0f)));
            ctx.drawArc(cx - radius, cy - radius, cx + radius, cy + radius, 0.0f, 360.0f, false, ripplePaint);
            ripplePaint.setStrokeCap(Paint.StrokeCap.FILL);
        }

        ctx.restore();
    }

    private void drawSoftSpecularEdge(DrawContext ctx, RoundedRectangle rect, float press) {
        float width = rect.getWidth();
        float height = rect.getHeight();
        float radius = Math.min(Math.min(rect.topLeftRadius, rect.topRightRadius), Math.min(rect.bottomLeftRadius, rect.bottomRightRadius));

        ctx.save();
        ctx.clipRoundedRect(rect, true);

        detailPaint.setGradCoords(null);
        detailPaint.setStrokeCap(Paint.StrokeCap.FILL);

        detailPaint.setColor(Argb.withAlpha(0xFFFFFFFF, Math.round(18.0f + press * 12.0f)));
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(
                rect.x1 + radius * 0.80f,
                rect.y1 + 1.05f,
                width - radius * 1.60f,
                0.85f,
                0.45f
        ), detailPaint);

        detailPaint.setColor(Argb.withAlpha(0xFF000000, Math.round(18.0f + press * 10.0f)));
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(
                rect.x1 + radius * 0.86f,
                rect.y1 + height - 1.90f,
                width - radius * 1.72f,
                0.90f,
                0.45f
        ), detailPaint);

        ctx.restore();
    }

    private void drawSpaceGlyph(DrawContext ctx, RoundedRectangle rect, float press) {
        float lineWidth = rect.getWidth() * 0.56f;
        float lineHeight = 2.0f;
        float lineX = rect.x1 + (rect.getWidth() - lineWidth) * 0.5f;
        float lineY = rect.y1 + (rect.getHeight() - lineHeight) * 0.5f;

        detailPaint.setGradCoords(null);
        detailPaint.setStrokeCap(Paint.StrokeCap.FILL);
        detailPaint.setColor(Argb.interpolate(0xD8FFFFFF, 0xFFFFFFFF, press));

        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(
                lineX,
                lineY,
                lineWidth,
                lineHeight,
                lineHeight * 0.5f
        ), detailPaint);
    }

    private void drawTextCentered(DrawContext ctx, String text, float centerX, float centerY, FontRenderer font, float press) {
        float textWidth = GlHelper.getStringWidth(text, font);
        float ascent = GlHelper.getFontAscent(font);
        float capHeight = font.getMetrics().capHeight();

        float drawX = centerX - textWidth * 0.5f;
        float drawY = centerY - capHeight * 0.5f - (ascent - capHeight);

        textPaint.setGradCoords(null);
        textPaint.setStrokeCap(Paint.StrokeCap.FILL);

        textPaint.setColor(Argb.withAlpha(0xFF000000, 82));
        ctx.drawString(text, drawX, drawY + 0.65f, font, textPaint);

        textPaint.setColor(resolveTextColor(press));
        ctx.drawString(text, drawX, drawY, font, textPaint);
    }

    private int resolveTextColor(float press) {
        int base = color(this.textColor, DEFAULT_TEXT).toArgb();
        return Argb.interpolate(base, Argb.withAlpha(0xFFFFFFFF, Argb.alpha(base)), press * 0.18f);
    }

    private static float easeOutCubic(float value) {
        float t = Mth.clamp(value, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private static boolean bool(Value<Boolean> value, boolean fallback) {
        return value == null ? fallback : Boolean.TRUE.equals(value.getValue());
    }

    private static float number(Value<Number> value, float fallback) {
        if (value == null || value.getValue() == null) {
            return fallback;
        }
        return value.getValue().floatValue();
    }

    private static MizuColor color(Value<MizuColor> value, MizuColor fallback) {
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
            this.rippleAnimation.setCurrentValue(1.0);
        }

        private boolean isDown() {
            return this.binding != null && this.binding.isDown();
        }

        private void update(boolean rippleEnabled) {
            boolean down = this.isDown();

            if (rippleEnabled && down && !this.wasDown) {
                this.rippleAnimation.setCurrentValue(0.0);
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
                }
            } else {
                this.rippleAnimation.setCurrentValue(1.0);
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