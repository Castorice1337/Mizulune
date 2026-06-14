package shit.zen.hud;

import java.util.ArrayDeque;
import java.util.Deque;
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
import shit.zen.value.MizuColor;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;
import shit.zen.value.impl.BooleanValue;
import shit.zen.value.impl.ModeValue;
import shit.zen.value.impl.NumberValue;

public class KeystrokesModule extends HudElement {

    private static final MizuColor DEFAULT_BACKGROUND_TOP = MizuColor.ofArgb(135, 20, 30, 52);
    private static final MizuColor DEFAULT_BACKGROUND_BOTTOM = MizuColor.ofArgb(165, 12, 20, 38);
    private static final MizuColor DEFAULT_PRESSED = MizuColor.ofArgb(185, 82, 155, 255);
    private static final MizuColor DEFAULT_OUTLINE = MizuColor.ofArgb(150, 135, 178, 255);
    private static final MizuColor DEFAULT_TEXT = MizuColor.ofArgb(245, 255, 255, 255);
    private static final MizuColor DEFAULT_ACCENT = MizuColor.ofArgb(255, 90, 184, 255);

    private final FontRenderer keyFont = FontPresets.poppinsBold(22.0f);
    private final FontRenderer mouseFont = FontPresets.poppinsBold(19.0f);
    private final FontRenderer cpsFont = FontPresets.poppinsMedium(10.5f);

    private final Paint fillPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint detailPaint = new Paint();
    private final Paint outlinePaint = new Paint()
            .setStrokeCap(Paint.StrokeCap.STROKE)
            .setStrokeJoin(Paint.StrokeJoin.ROUND);

    private Value<MizuColor> backgroundTopColor;
    private Value<MizuColor> backgroundBottomColor;
    private Value<MizuColor> pressedColor;
    private Value<MizuColor> outlineColor;
    private Value<MizuColor> textColor;
    private Value<MizuColor> accentColor;

    public final BooleanValue background = new BooleanValue("Background", true);
    public final BooleanValue outline = new BooleanValue("Outline", true);
    public final BooleanValue glow = new BooleanValue("Glow", true);
    public final BooleanValue blur = new BooleanValue("Blur", false);
    public final BooleanValue decorations = new BooleanValue("Decorations", true);
    public final BooleanValue showCPS = new BooleanValue("Show CPS", true);
    public final BooleanValue showSpace = new BooleanValue("Show Space", true);
    public final BooleanValue uppercaseSpace = new BooleanValue("Uppercase Space", false);

    public final ModeValue textMode = new ModeValue("Text Mode", "STATIC", "STATIC", "GRADIENT", "RAINBOW");

    public final NumberValue scale = new NumberValue("Scale", 1.0f, 0.45f, 2.0f, 0.05f);
    public final NumberValue keySize = new NumberValue("Key Size", 48.0f, 32.0f, 72.0f, 1.0f);
    public final NumberValue mouseKeyWidth = new NumberValue("Mouse Key Width", 86.0f, 60.0f, 140.0f, 1.0f);
    public final NumberValue spaceWidth = new NumberValue("Space Width", 180.0f, 120.0f, 300.0f, 1.0f);
    public final NumberValue keyHeight = new NumberValue("Key Height", 48.0f, 32.0f, 72.0f, 1.0f);
    public final NumberValue gap = new NumberValue("Gap", 8.0f, 2.0f, 18.0f, 0.5f);
    public final NumberValue radius = new NumberValue("Radius", 13.0f, 2.0f, 24.0f, 1.0f);
    public final NumberValue glowStrength = new NumberValue("Glow Strength", 10.0f, 2.0f, 28.0f, 1.0f);
    public final NumberValue animationSpeed = new NumberValue("Animation Speed", 0.18f, 0.06f, 0.40f, 0.01f);

    private final KeyButton keyW;
    private final KeyButton keyA;
    private final KeyButton keyS;
    private final KeyButton keyD;
    private final MouseKeyButton keyLMB;
    private final MouseKeyButton keyRMB;
    private final KeyButton keySpace;

    public KeystrokesModule() {
        super("Keystrokes");

        this.keyW = new KeyButton("W", mc.options.keyUp);
        this.keyA = new KeyButton("A", mc.options.keyLeft);
        this.keyS = new KeyButton("S", mc.options.keyDown);
        this.keyD = new KeyButton("D", mc.options.keyRight);
        this.keyLMB = new MouseKeyButton("LMB", mc.options.keyAttack);
        this.keyRMB = new MouseKeyButton("RMB", mc.options.keyUse);
        this.keySpace = new KeyButton("Space", mc.options.keyJump);

        this.setX(100.0f);
        this.setY(100.0f);
        this.setWidth(180.0f);
        this.setHeight(216.0f);
        this.setEnabled(true);
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup colors = root.group("colors", "Colors");
        this.backgroundTopColor = colors.color("background_top", "Background Top", DEFAULT_BACKGROUND_TOP);
        this.backgroundBottomColor = colors.color("background_bottom", "Background Bottom", DEFAULT_BACKGROUND_BOTTOM);
        this.pressedColor = colors.color("pressed", "Pressed", DEFAULT_PRESSED);
        this.outlineColor = colors.color("outline", "Outline", DEFAULT_OUTLINE);
        this.textColor = colors.color("text", "Text", DEFAULT_TEXT);
        this.accentColor = colors.color("accent", "Accent", DEFAULT_ACCENT);
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

        this.setWidth(layout.totalWidth * layout.scale);
        this.setHeight(layout.totalHeight * layout.scale);

        ctx.save();
        ctx.translate(x, y);
        ctx.scale(layout.scale, layout.scale);

        drawKey(ctx, keyW, layout.wX, layout.row1Y, layout.keySize, layout.keyHeight, 0);
        drawKey(ctx, keyA, layout.aX, layout.row2Y, layout.keySize, layout.keyHeight, 1);
        drawKey(ctx, keyS, layout.sX, layout.row2Y, layout.keySize, layout.keyHeight, 2);
        drawKey(ctx, keyD, layout.dX, layout.row2Y, layout.keySize, layout.keyHeight, 3);
        drawMouseKey(ctx, keyLMB, layout.lmbX, layout.row3Y, layout.mouseWidth, layout.keyHeight, 4);
        drawMouseKey(ctx, keyRMB, layout.rmbX, layout.row3Y, layout.mouseWidth, layout.keyHeight, 5);

        if (this.showSpace.getValue()) {
            String label = this.uppercaseSpace.getValue() ? "SPACE" : "Space";
            drawKey(ctx, keySpace.withRenderName(label), layout.spaceX, layout.row4Y, layout.spaceWidth, layout.keyHeight, 6);
        }

        ctx.restore();
    }

    private Layout buildLayout() {
        float s = this.scale.getValue().floatValue();
        float key = this.keySize.getValue().floatValue();
        float h = this.keyHeight.getValue().floatValue();
        float g = this.gap.getValue().floatValue();

        float mouseW = this.mouseKeyWidth.getValue().floatValue();
        float spaceW = this.spaceWidth.getValue().floatValue();

        float row2W = key * 3.0f + g * 2.0f;
        float row3W = mouseW * 2.0f + g;
        float totalW = this.showSpace.getValue()
                ? Math.max(spaceW, Math.max(row2W, row3W))
                : Math.max(row2W, row3W);

        float row1Y = 0.0f;
        float row2Y = row1Y + h + g;
        float row3Y = row2Y + h + g;
        float row4Y = row3Y + h + g;

        float totalH = this.showSpace.getValue() ? row4Y + h : row3Y + h;

        float wX = (totalW - key) * 0.5f;

        float row2X = (totalW - row2W) * 0.5f;
        float aX = row2X;
        float sX = row2X + key + g;
        float dX = row2X + (key + g) * 2.0f;

        float row3X = (totalW - row3W) * 0.5f;
        float lmbX = row3X;
        float rmbX = row3X + mouseW + g;

        float spaceX = (totalW - spaceW) * 0.5f;

        return new Layout(s, key, h, mouseW, spaceW, totalW, totalH,
                row1Y, row2Y, row3Y, row4Y,
                wX, aX, sX, dX, lmbX, rmbX, spaceX);
    }

    private void drawKey(DrawContext ctx, KeyButton button, float x, float y, float width, float height, int index) {
        button.update();
        float press = button.getPress();
        float r = effectiveRadius(width, height);

        ctx.save();
        applyPressScale(ctx, x, y, width, height, press);

        drawButtonBase(ctx, x, y, width, height, r, press, index);

        int color = resolveTextColor(index, press);
        drawTextCentered(button.renderName, x + width * 0.5f, y + height * 0.5f, keyFont, color);

        ctx.restore();
    }

    private void drawMouseKey(DrawContext ctx, MouseKeyButton button, float x, float y, float width, float height, int index) {
        button.update();
        float press = button.getPress();
        float r = effectiveRadius(width, height);

        ctx.save();
        applyPressScale(ctx, x, y, width, height, press);

        drawButtonBase(ctx, x, y, width, height, r, press, index);

        int mainColor = resolveTextColor(index, press);
        int cpsColor = lerp(color(accentColor, DEFAULT_ACCENT).withAlpha(170).toArgb(),
                color(accentColor, DEFAULT_ACCENT).withAlpha(255).toArgb(), press);

        if (this.showCPS.getValue()) {
            String cps = button.getCps() + " CPS";

            float mainCap = mouseFont.getMetrics().capHeight();
            float cpsCap = cpsFont.getMetrics().capHeight();
            float spacing = 5.0f;
            float total = mainCap + spacing + cpsCap;
            float start = y + (height - total) * 0.5f;

            drawTextCentered(button.renderName, x + width * 0.5f, start + mainCap * 0.5f, mouseFont, mainColor);
            drawTextCentered(cps, x + width * 0.5f, start + mainCap + spacing + cpsCap * 0.5f, cpsFont, cpsColor);
        } else {
            drawTextCentered(button.renderName, x + width * 0.5f, y + height * 0.5f, mouseFont, mainColor);
        }

        ctx.restore();
    }

    private void drawButtonBase(DrawContext ctx, float x, float y, float width, float height, float r, float press, int index) {
        MizuColor top = color(backgroundTopColor, DEFAULT_BACKGROUND_TOP);
        MizuColor bottom = color(backgroundBottomColor, DEFAULT_BACKGROUND_BOTTOM);
        MizuColor pressed = color(pressedColor, DEFAULT_PRESSED);
        MizuColor outlineCol = color(outlineColor, DEFAULT_OUTLINE);
        MizuColor accent = color(accentColor, DEFAULT_ACCENT);

        int topColor = lerp(top.toArgb(), pressed.withAlpha(Math.max(top.alpha(), pressed.alpha())).toArgb(), press);
        int bottomColor = lerp(bottom.toArgb(), pressed.withAlpha(Math.max(bottom.alpha(), pressed.alpha())).toArgb(), press);
        int edgeColor = lerp(outlineCol.toArgb(), accent.toArgb(), press);

        RoundedRectangle rect = RoundedRectangle.ofXYWHR(x, y, width, height, r);

        if (this.glow.getValue()) {
            float glowAlpha = 0.35f + press * 0.65f;
            int glowColor = multiplyAlpha(edgeColor, glowAlpha);
            ctx.drawBlurredRoundedRect(rect, 0.0f, 0.0f,
                    this.glowStrength.getValue().floatValue(), 1.25f, glowColor);
        }

        if (this.blur.getValue()) {
            ctx.drawBlur(x, y, width, height, 7.0f, () -> {
                fillPaint.setGradCoords(new Paint.GradientCoords(x, y, x, y + height, topColor, bottomColor));
                ctx.drawRoundedRect(rect, fillPaint);
            });
        }

        if (this.background.getValue()) {
            fillPaint.setGradCoords(new Paint.GradientCoords(x, y, x, y + height, topColor, bottomColor));
            ctx.drawRoundedRect(rect, fillPaint);
        }

        if (this.decorations.getValue()) {
            drawInnerHighlight(ctx, x, y, width, height, r, edgeColor, press);
            drawDotMatrix(ctx, x, y, width, height, edgeColor, index);
        }

        if (this.outline.getValue()) {
            outlinePaint.setGradCoords(null);
            outlinePaint.setStrokeWidth(1.25f + press * 0.65f);
            outlinePaint.setColor(multiplyAlpha(edgeColor, 0.72f + press * 0.28f));
            ctx.drawRoundedRect(rect, outlinePaint);
        }
    }

    private void drawInnerHighlight(DrawContext ctx, float x, float y, float width, float height, float radius, int edgeColor, float press) {
        float inset = 2.0f;
        float lineHeight = 1.0f;
        int topLine = multiplyAlpha(edgeColor, 0.34f + press * 0.26f);
        int bottomLine = multiplyAlpha(edgeColor, 0.16f + press * 0.16f);

        detailPaint.setGradCoords(null);
        detailPaint.setColor(topLine);
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(
                x + radius * 0.55f,
                y + inset,
                width - radius * 1.1f,
                lineHeight,
                lineHeight * 0.5f
        ), detailPaint);

        detailPaint.setColor(bottomLine);
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(
                x + radius * 0.65f,
                y + height - inset - lineHeight,
                width - radius * 1.3f,
                lineHeight,
                lineHeight * 0.5f
        ), detailPaint);
    }

    private void drawDotMatrix(DrawContext ctx, float x, float y, float width, float height, int color, int index) {
        float dot = 1.05f;
        float step = 5.0f;
        int columns = Math.max(4, Math.min(12, (int) ((width - 18.0f) / step)));
        int rows = Math.max(2, Math.min(3, (int) ((height - 20.0f) / 7.0f)));

        float matrixW = (columns - 1) * step + dot;
        float startX = x + (width - matrixW) * 0.5f;
        float startY = y + height - 14.0f;

        int dotColor = multiplyAlpha(color, 0.12f);

        detailPaint.setGradCoords(null);
        detailPaint.setColor(dotColor);

        for (int row = 0; row < rows; row++) {
            float alphaFactor = 1.0f - row * 0.22f;
            detailPaint.setColor(multiplyAlpha(dotColor, alphaFactor));

            for (int col = 0; col < columns; col++) {
                if (((col + row + index) % 5) == 0) {
                    continue;
                }

                ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(
                        startX + col * step,
                        startY + row * 4.0f,
                        dot,
                        dot,
                        dot * 0.5f
                ), detailPaint);
            }
        }
    }

    private void applyPressScale(DrawContext ctx, float x, float y, float width, float height, float press) {
        float scale = 1.0f + press * 0.035f;
        float cx = x + width * 0.5f;
        float cy = y + height * 0.5f;

        ctx.translate(cx, cy);
        ctx.scale(scale, scale);
        ctx.translate(-cx, -cy);
    }

    private void drawTextCentered(String text, float centerX, float centerY, FontRenderer font, int color) {
        float textWidth = GlHelper.getStringWidth(text, font);
        float ascent = GlHelper.getFontAscent(font);
        float capHeight = font.getMetrics().capHeight();

        float drawX = centerX - textWidth * 0.5f;
        float drawY = centerY - capHeight * 0.5f - (ascent - capHeight);

        textPaint.setGradCoords(null);
        textPaint.setColor(color);
        GlHelper.drawTextWithShadow(text, drawX, drawY, font, textPaint);
    }

    private int resolveTextColor(int index, float press) {
        MizuColor text = color(textColor, DEFAULT_TEXT);
        MizuColor accent = color(accentColor, DEFAULT_ACCENT);

        if (this.textMode.is("RAINBOW")) {
            float hue = ((System.currentTimeMillis() / 14L + index * 22L) % 360L) / 360.0f;
            return MizuColor.ofHsb(hue, 0.50f, 1.0f, text.alpha()).toArgb();
        }

        if (this.textMode.is("GRADIENT")) {
            float t = Mth.clamp(index / 6.0f + press * 0.18f, 0.0f, 1.0f);
            return text.interpolateTo(accent, t).toArgb();
        }

        return text.interpolateTo(accent.withAlpha(text.alpha()), press * 0.35f).toArgb();
    }

    private float effectiveRadius(float width, float height) {
        float max = Math.min(width, height) * 0.28f;
        return Mth.clamp(this.radius.getValue().floatValue(), 2.0f, max);
    }

    private MizuColor color(Value<MizuColor> value, MizuColor fallback) {
        return value != null && value.getValue() != null ? value.getValue() : fallback;
    }

    private int lerp(int a, int b, float progress) {
        progress = Mth.clamp(progress, 0.0f, 1.0f);

        int aa = a >>> 24;
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;

        int ba = b >>> 24;
        int br = b >> 16 & 0xFF;
        int bg = b >> 8 & 0xFF;
        int bb = b & 0xFF;

        int ca = Math.round(aa + (ba - aa) * progress);
        int cr = Math.round(ar + (br - ar) * progress);
        int cg = Math.round(ag + (bg - ag) * progress);
        int cb = Math.round(ab + (bb - ab) * progress);

        return ca << 24 | cr << 16 | cg << 8 | cb;
    }

    private int multiplyAlpha(int color, float alphaMultiplier) {
        int alpha = color >>> 24;
        alpha = Math.round(alpha * Mth.clamp(alphaMultiplier, 0.0f, 1.0f));
        return alpha << 24 | color & 0x00FFFFFF;
    }

    private static final class Layout {
        final float scale;
        final float keySize;
        final float keyHeight;
        final float mouseWidth;
        final float spaceWidth;
        final float totalWidth;
        final float totalHeight;

        final float row1Y;
        final float row2Y;
        final float row3Y;
        final float row4Y;

        final float wX;
        final float aX;
        final float sX;
        final float dX;
        final float lmbX;
        final float rmbX;
        final float spaceX;

        Layout(float scale,
               float keySize,
               float keyHeight,
               float mouseWidth,
               float spaceWidth,
               float totalWidth,
               float totalHeight,
               float row1Y,
               float row2Y,
               float row3Y,
               float row4Y,
               float wX,
               float aX,
               float sX,
               float dX,
               float lmbX,
               float rmbX,
               float spaceX) {
            this.scale = scale;
            this.keySize = keySize;
            this.keyHeight = keyHeight;
            this.mouseWidth = mouseWidth;
            this.spaceWidth = spaceWidth;
            this.totalWidth = totalWidth;
            this.totalHeight = totalHeight;
            this.row1Y = row1Y;
            this.row2Y = row2Y;
            this.row3Y = row3Y;
            this.row4Y = row4Y;
            this.wX = wX;
            this.aX = aX;
            this.sX = sX;
            this.dX = dX;
            this.lmbX = lmbX;
            this.rmbX = rmbX;
            this.spaceX = spaceX;
        }
    }

    private class KeyButton {
        final String name;
        final KeyMapping binding;
        final SmoothAnimationTimer pressAnimation = new SmoothAnimationTimer();
        String renderName;

        KeyButton(String name, KeyMapping binding) {
            this.name = name;
            this.renderName = name;
            this.binding = binding;
            this.pressAnimation.setCurrentValue(0.0);
        }

        KeyButton withRenderName(String renderName) {
            this.renderName = renderName;
            return this;
        }

        boolean isDown() {
            return this.binding != null && this.binding.isDown();
        }

        void update() {
            float speed = animationSpeed.getValue().floatValue();
            this.pressAnimation.animate(this.isDown() ? 1.0 : 0.0, speed, Easings.EASE_OUT_POW3);
            this.pressAnimation.tick();
        }

        float getPress() {
            return this.pressAnimation.getValueF();
        }
    }

    private final class MouseKeyButton extends KeyButton {
        private final Deque<Long> clicks = new ArrayDeque<>();
        private boolean wasDown;

        MouseKeyButton(String name, KeyMapping binding) {
            super(name, binding);
        }

        @Override
        void update() {
            this.sampleClick();
            super.update();
            this.prune();
        }

        int getCps() {
            this.prune();
            return this.clicks.size();
        }

        private void sampleClick() {
            boolean down = this.isDown();

            if (down && !this.wasDown) {
                this.clicks.addLast(System.currentTimeMillis());
            }

            this.wasDown = down;
        }

        private void prune() {
            long now = System.currentTimeMillis();

            while (!this.clicks.isEmpty() && now - this.clicks.peekFirst() > 1000L) {
                this.clicks.removeFirst();
            }
        }
    }
}