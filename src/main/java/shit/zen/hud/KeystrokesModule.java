package shit.zen.hud;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.GlRenderEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.render.DrawContext;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlyphMetrics;
import shit.zen.render.GlHelper;
import shit.zen.render.Paint;
import shit.zen.render.RoundedRectangle;
import shit.zen.render.LiquidGlassStyle;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.value.MizuColor;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;
import shit.zen.value.impl.BooleanValue;
import shit.zen.value.impl.ModeValue;
import shit.zen.value.impl.NumberValue;

public class KeystrokesModule extends HudElement {
    
    // Aesthetic Settings
    private Value<MizuColor> backgroundColor;
    private Value<MizuColor> pressedColor;
    private Value<MizuColor> outlineColor;
    private Value<MizuColor> textColor;
    private Value<MizuColor> accentColor;
    
    public final BooleanValue blur = new BooleanValue("Blur", false);
    public final BooleanValue glow = new BooleanValue("Glow", false);
    public final BooleanValue background = new BooleanValue("Background", true);
    public final BooleanValue outline = new BooleanValue("Outline", true);
    public final BooleanValue showCPS = new BooleanValue("Show CPS", true);
    public final BooleanValue showSpace = new BooleanValue("Show Space", true);
    public final BooleanValue uppercaseSpace = new BooleanValue("Uppercase Space", false);
    
    public final ModeValue textMode = new ModeValue("Text Mode", "STATIC", "STATIC", "GRADIENT", "RAINBOW");
    
    // Dimensions
    public final NumberValue scale = new NumberValue("Scale", 1.0f, 0.5f, 2.0f, 0.1f);
    public final NumberValue keySize = new NumberValue("Key Size", 36.0f, 20.0f, 60.0f, 1.0f);
    public final NumberValue mouseKeyWidth = new NumberValue("Mouse Key Width", 56.0f, 30.0f, 100.0f, 1.0f);
    public final NumberValue spaceWidth = new NumberValue("Space Width", 116.0f, 50.0f, 200.0f, 1.0f);
    public final NumberValue keyHeight = new NumberValue("Key Height", 36.0f, 20.0f, 60.0f, 1.0f);
    public final NumberValue gap = new NumberValue("Gap", 4.0f, 0.0f, 10.0f, 0.5f);
    public final NumberValue radius = new NumberValue("Radius", 8.0f, 0.0f, 20.0f, 1.0f);
    public final NumberValue glowStrength = new NumberValue("Glow Strength", 8.0f, 1.0f, 20.0f, 1.0f);
    
    private final FontRenderer font = FontPresets.poppinsMedium(18.0f);
    private final FontRenderer cpsFont = FontPresets.poppinsRegular(12.0f);
    
    private final Paint paint = new Paint();
    private final Paint outlinePaint = new Paint().setStrokeCap(Paint.StrokeCap.STROKE).setStrokeJoin(Paint.StrokeJoin.ROUND);

    private final KeyButton keyW;
    private final KeyButton keyA;
    private final KeyButton keyS;
    private final KeyButton keyD;
    private final MouseKeyButton keyLMB;
    private final MouseKeyButton keyRMB;
    private final KeyButton keySpace;

    public KeystrokesModule() {
        super("Keystrokes");
        this.setWidth(116.0f);
        this.setHeight(150.0f);
        this.setX(100.0f);
        this.setY(100.0f);
        this.setEnabled(true);
        
        this.keyW = new KeyButton("W", mc.options.keyUp);
        this.keyA = new KeyButton("A", mc.options.keyLeft);
        this.keyS = new KeyButton("S", mc.options.keyDown);
        this.keyD = new KeyButton("D", mc.options.keyRight);
        this.keyLMB = new MouseKeyButton("LMB", mc.options.keyAttack);
        this.keyRMB = new MouseKeyButton("RMB", mc.options.keyUse);
        this.keySpace = new KeyButton("Space", mc.options.keyJump);
    }
    
    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup colors = root.group("colors", "Colors");
        this.backgroundColor = colors.color("background", "Background", MizuColor.ofArgb(120, 20, 28, 45));
        this.pressedColor = colors.color("pressed", "Pressed", MizuColor.ofArgb(150, 80, 150, 255));
        this.outlineColor = colors.color("outline", "Outline", MizuColor.ofArgb(100, 150, 200, 255));
        this.textColor = colors.color("text", "Text", MizuColor.ofArgb(255, 255, 255, 255));
        this.accentColor = colors.color("accent", "Accent", MizuColor.ofArgb(255, 100, 150, 255));
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null) return;
        keyLMB.tickCps();
        keyRMB.tickCps();
    }

    @Override
    public void onRender2D(Render2DEvent event, float x, float y) {
        // Not used, rendering in onGlRender
    }

    @Override
    public void onGlRender(GlRenderEvent event, float x, float y) {
        if (mc.player == null) return;
        
        float s = this.scale.getValue().floatValue();
        float sz = this.keySize.getValue().floatValue();
        float mWidth = this.mouseKeyWidth.getValue().floatValue();
        float spWidth = this.spaceWidth.getValue().floatValue();
        float h = this.keyHeight.getValue().floatValue();
        float g = this.gap.getValue().floatValue();
        
        // Calculate total bounds for Hitbox/Dragging
        float totalWidth = spWidth;
        float totalHeight = (h * 4) + (g * 3);
        if (!this.showSpace.getValue()) {
            totalHeight -= (h + g);
        }
        this.setWidth(totalWidth * s);
        this.setHeight(totalHeight * s);
        
        DrawContext drawCtx = (DrawContext) event.drawContext();
        drawCtx.save();
        drawCtx.translate(x, y);
        drawCtx.scale(s, s);
        
        // Row 1: W
        float wX = (totalWidth - sz) / 2.0f;
        drawKey(drawCtx, keyW, wX, 0, sz, h);
        
        // Row 2: A, S, D
        float asdY = h + g;
        float row2Width = (sz * 3) + (g * 2);
        float aX = (totalWidth - row2Width) / 2.0f;
        drawKey(drawCtx, keyA, aX, asdY, sz, h);
        drawKey(drawCtx, keyS, aX + sz + g, asdY, sz, h);
        drawKey(drawCtx, keyD, aX + (sz + g) * 2, asdY, sz, h);
        
        // Row 3: LMB, RMB
        float lrmbY = asdY + h + g;
        float row3Width = (mWidth * 2) + g;
        float lmbX = (totalWidth - row3Width) / 2.0f;
        drawKey(drawCtx, keyLMB, lmbX, lrmbY, mWidth, h);
        drawKey(drawCtx, keyRMB, lmbX + mWidth + g, lrmbY, mWidth, h);
        
        // Row 4: Space
        if (this.showSpace.getValue()) {
            float spaceY = lrmbY + h + g;
            float spaceX = (totalWidth - spWidth) / 2.0f;
            drawKey(drawCtx, keySpace, spaceX, spaceY, spWidth, h);
        }
        
        drawCtx.restore();
    }
    
    private void drawTextCentered(DrawContext drawCtx, float centerX, float centerY, String text, FontRenderer fontRenderer, Paint paint) {
        float width = GlHelper.getStringWidth(text, fontRenderer);
        float height = fontRenderer.getFont() != null ? fontRenderer.getFont().getFontHeight() : fontRenderer.getSize();
        float tx = centerX - width / 2.0f;
        float ty = centerY - height / 2.0f;
        float baselineY = ty + getFontAscent(fontRenderer);
        drawCtx.drawString(text, tx, baselineY, fontRenderer, paint);
    }
    
    private float getFontAscent(FontRenderer fontRenderer) {
        GlyphMetrics m = fontRenderer.getMetrics();
        return (float) Math.ceil((m.getLineGap() - m.ascent() - m.descent()) / 2.0f);
    }
    
    private void drawKey(DrawContext drawCtx, KeyButton button, float offsetX, float offsetY, float width, float height) {
        button.updateAnim();
        float anim = button.pressAnim.getValueF();
        
        float r = this.radius.getValue().floatValue();
        boolean drawBg = this.background.getValue();
        boolean drawOutline = this.outline.getValue();
        boolean drawGlow = this.glow.getValue();
        boolean drawBlur = this.blur.getValue();
        float glStr = this.glowStrength.getValue().floatValue();
        
        // Scale effect
        float centerOffX = offsetX + width / 2.0f;
        float centerOffY = offsetY + height / 2.0f;
        
        drawCtx.save();
        drawCtx.translate(centerOffX, centerOffY);
        float scaleVal = 1.0f + (anim * 0.04f);
        drawCtx.scale(scaleVal, scaleVal);
        drawCtx.translate(-centerOffX, -centerOffY);
        
        MizuColor bgCol = this.backgroundColor.getValue();
        MizuColor prCol = this.pressedColor.getValue();
        MizuColor outCol = this.outlineColor.getValue();
        MizuColor textCol = this.textColor.getValue();
        MizuColor accCol = this.accentColor.getValue();
        
        MizuColor currentBg = bgCol.interpolateTo(prCol, anim);
        
        if (drawGlow) {
            MizuColor currentGlow = outCol.interpolateTo(accCol.withAlpha(255), anim);
            float glowAlpha = Math.max(0.1f, anim);
            drawCtx.drawBlurredRoundedRect(RoundedRectangle.ofXYWHR(offsetX, offsetY, width, height, r), 0.0f, 0.0f, glStr, 2.0f, currentGlow.withAlpha((int)(255 * glowAlpha)).toArgb());
        }
        
        if (drawBlur) {
            LiquidGlassStyle style = LiquidGlassStyle.builder()
                    .power(r)
                    .refractionPower(1.0f)
                    .refractionStrength(0.0f)
                    .noise(0.0f)
                    .glow(0.0f, 0.0f)
                    .glowEdges(0.0f, 0.85f)
                    .blurIterations(2)
                    .blurRadius(10.0f)
                    .blurDownscale(1.0f)
                    .opacity(currentBg.alpha() / 255.0f)
                    .tint(currentBg.toArgb() & 0x00FFFFFF, currentBg.alpha() / 255.0f)
                    .chromaStrength(0.0f)
                    .darkness(0.0f)
                    .build();
            drawCtx.save();
            drawCtx.clipRoundedRect(RoundedRectangle.ofXYWHR(offsetX, offsetY, width, height, r), true);
            drawCtx.drawLiquidGlassPanel(RoundedRectangle.ofXYWHR(offsetX, offsetY, width, height, r), style);
            drawCtx.restore();
        } else if (drawBg) {
            paint.setColor(currentBg.toArgb());
            drawCtx.drawRoundedRect(RoundedRectangle.ofXYWHR(offsetX, offsetY, width, height, r), paint);
        }
        
        if (drawOutline) {
            outlinePaint.setColor(outCol.interpolateTo(accCol, anim).toArgb());
            outlinePaint.setStrokeWidth(1.0f);
            drawCtx.drawRoundedRect(RoundedRectangle.ofXYWHR(offsetX, offsetY, width, height, r), outlinePaint);
        }
        
        // Draw Text
        String name = button.name;
        if (button == keySpace && this.uppercaseSpace.getValue()) {
            name = name.toUpperCase();
        }
        
        MizuColor currentTextCol = textCol;
        if (this.textMode.is("GRADIENT") || this.textMode.is("RAINBOW")) {
            currentTextCol = textCol.interpolateTo(accCol, anim);
        }
        
        float textY = offsetY + (height / 2.0f);
        boolean isMouseKey = button instanceof MouseKeyButton;
        
        if (isMouseKey && this.showCPS.getValue()) {
            MouseKeyButton mouseBtn = (MouseKeyButton) button;
            String cpsText = mouseBtn.getCps() + " CPS";
            
            float nameH = getFontAscent(font);
            float cpsH = getFontAscent(cpsFont);
            float totalH = nameH + cpsH + 2.0f;
            float startY = offsetY + (height - totalH) / 2.0f;
            
            this.paint.setColor(currentTextCol.toArgb());
            drawTextCentered(drawCtx, offsetX + width / 2.0f, startY + nameH / 2.0f, name, font, this.paint);
            
            this.paint.setColor(accCol.withAlpha((int)(255 * (0.5f + anim * 0.5f))).toArgb());
            drawTextCentered(drawCtx, offsetX + width / 2.0f, startY + nameH + 2.0f + cpsH / 2.0f, cpsText, cpsFont, this.paint);
        } else {
            this.paint.setColor(currentTextCol.toArgb());
            drawTextCentered(drawCtx, offsetX + width / 2.0f, textY, name, font, this.paint);
        }
        
        drawCtx.restore();
    }
    
    private class KeyButton {
        final String name;
        final KeyMapping binding;
        final SmoothAnimationTimer pressAnim = new SmoothAnimationTimer();
        
        KeyButton(String name, KeyMapping binding) {
            this.name = name;
            this.binding = binding;
            this.pressAnim.setCurrentValue(0.0);
        }
        
        void updateAnim() {
            boolean down = binding.isDown();
            this.pressAnim.animate(down ? 1.0 : 0.0, down ? 0.15 : 0.25, Easings.EASE_OUT_POW3);
            this.pressAnim.tick();
        }
    }
    
    private class MouseKeyButton extends KeyButton {
        private final Deque<Long> clicks = new ArrayDeque<>();
        private boolean wasDown = false;
        
        MouseKeyButton(String name, KeyMapping binding) {
            super(name, binding);
        }
        
        void tickCps() {
            boolean down = binding.isDown();
            if (down && !wasDown) {
                clicks.addLast(System.currentTimeMillis());
            }
            wasDown = down;
            
            long now = System.currentTimeMillis();
            while (!clicks.isEmpty() && now - clicks.getFirst() > 1000L) {
                clicks.removeFirst();
            }
        }
        
        int getCps() {
            return clicks.size();
        }
    }
}
