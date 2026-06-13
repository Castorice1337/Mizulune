package shit.zen.hud;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import shit.zen.ZenClient;
import shit.zen.event.impl.GlRenderEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.manager.ConfigManager;
import shit.zen.modules.Module;
import shit.zen.modules.impl.render.Interface;
import shit.zen.render.DrawContext;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
import shit.zen.render.LiquidGlassStyle;
import shit.zen.render.Paint;
import shit.zen.render.Renderer;
import shit.zen.render.RoundedRectangle;
import shit.zen.settings.impl.BooleanSetting;
import shit.zen.settings.impl.ModeSetting;
import shit.zen.settings.impl.NumberSetting;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.utils.render.ColorUtil;

public class ModuleListHud extends HudElement {
    private enum Alignment {
        LEFT,
        RIGHT
    }

    private static final class AnimatedRow {
        private final Module module;
        private final SmoothAnimationTimer progressAnim = new SmoothAnimationTimer();
        private String name;
        private float textWidth;
        private float rowWidth;
        private boolean targetVisible;

        private AnimatedRow(Module module) {
            this.module = module;
            this.name = module.getName();
            this.progressAnim.setCurrentValue(0.0f);
            this.progressAnim.setToValue(0.0f);
        }

        private void updateMetrics(String name, float textWidth, float rowWidth) {
            this.name = name;
            this.textWidth = textWidth;
            this.rowWidth = rowWidth;
        }

        private void setTargetVisible(boolean visible) {
            if (this.targetVisible == visible) {
                return;
            }
            this.targetVisible = visible;
            this.progressAnim.animate(visible ? 1.0 : 0.0, visible ? 0.24 : 0.18,
                    visible ? Easings.EASE_OUT_POW3 : Easings.EASE_IN_POW3);
        }

        private void tick() {
            this.progressAnim.tick();
        }

        private float progress() {
            return Mth.clamp(this.progressAnim.getValueF(), 0.0f, 1.0f);
        }

        private boolean isFinishedRemoving() {
            return !this.targetVisible && this.progress() <= 0.01f && this.progressAnim.isDone();
        }
    }

    private static final float MIN_VISIBLE_EDGE = 4.0f;
    private static final float DEFAULT_ROW_HEIGHT = 14.0f;
    private static final float DEFAULT_PADDING_X = 4.0f;
    private static final float DEFAULT_PADDING_Y = 1.0f;
    private static final float DEFAULT_ROW_SPACING = 0.0f;
    private static final float DEFAULT_RADIUS = 1.5f;
    private static final float RAINBOW_TEXT_SWEEP_DEGREES = 180.0f;
    private static final float RAINBOW_LIST_SWEEP_DEGREES = 360.0f;
    private static final float SLIDE_DISTANCE = 18.0f;

    public final ModeSetting sideMode = new ModeSetting("Side Mode", "Auto", "Left", "Right").withDefault("Auto");
    public final BooleanSetting backgroundEnabled = new BooleanSetting("Background", true);
    public final NumberSetting backgroundColor = new NumberSetting("Background Color", 0x000000, 0, 0xFFFFFF, 1);
    public final NumberSetting backgroundAlpha = new NumberSetting("Background Alpha", 80, 0, 255, 1);
    public final NumberSetting backgroundRadius = new NumberSetting("Background Radius", DEFAULT_RADIUS, 0.0f, 10.0f, 0.25f);
    public final NumberSetting paddingX = new NumberSetting("Padding X", DEFAULT_PADDING_X, 0.0f, 12.0f, 0.25f);
    public final NumberSetting paddingY = new NumberSetting("Padding Y", DEFAULT_PADDING_Y, 0.0f, 8.0f, 0.25f);
    public final NumberSetting rowHeight = new NumberSetting("Row Height", DEFAULT_ROW_HEIGHT, 9.0f, 24.0f, 0.25f);
    public final NumberSetting rowSpacing = new NumberSetting("Row Spacing", DEFAULT_ROW_SPACING, 0.0f, 8.0f, 0.25f);

    public final BooleanSetting backgroundBlurEnabled = new BooleanSetting("Background Blur", false);
    public final NumberSetting blurRadius = new NumberSetting("Blur Radius", 10.0f, 0.0f, 32.0f, 0.5f,
            () -> this.backgroundBlurEnabled.getValue());
    public final NumberSetting blurStrength = new NumberSetting("Blur Strength", 0.55f, 0.0f, 1.0f, 0.01f,
            () -> this.backgroundBlurEnabled.getValue());

    public final BooleanSetting backgroundGlowEnabled = new BooleanSetting("Background Glow", true);
    public final NumberSetting glowRadius = new NumberSetting("Glow Radius", 5.0f, 0.0f, 24.0f, 0.5f,
            () -> this.backgroundGlowEnabled.getValue());
    public final NumberSetting glowAlpha = new NumberSetting("Glow Alpha", 42, 0, 255, 1,
            () -> this.backgroundGlowEnabled.getValue());
    public final NumberSetting glowIterations = new NumberSetting("Glow Iterations", 3, 1, 8, 1,
            () -> this.backgroundGlowEnabled.getValue());

    public final BooleanSetting sideLineEnabled = new BooleanSetting("Side Line", true);
    public final ModeSetting sideLineMode = new ModeSetting("Side Line Mode", "Auto", "Left", "Right")
            .withDefault("Auto")
            .withVisibility(() -> this.sideLineEnabled.getValue());
    public final NumberSetting sideLineWidth = new NumberSetting("Side Line Width", 2.0f, 0.5f, 5.0f, 0.25f,
            () -> this.sideLineEnabled.getValue());

    public final BooleanSetting textGradientEnabled = new BooleanSetting("Text Gradient", true);
    public final NumberSetting gradientColorStart = new NumberSetting("Gradient Color Start", 0xEAF7FF, 0, 0xFFFFFF, 1,
            () -> this.textGradientEnabled.getValue());
    public final NumberSetting gradientColorEnd = new NumberSetting("Gradient Color End", 0xFFC4F1, 0, 0xFFFFFF, 1,
            () -> this.textGradientEnabled.getValue());
    public final ModeSetting gradientMode = new ModeSetting("Gradient Mode", "Vertical List", "Horizontal Text")
            .withDefault("Vertical List")
            .withVisibility(() -> this.textGradientEnabled.getValue() && !this.dynamicGradientEnabled.getValue());

    public final BooleanSetting rainbowEnabled = new BooleanSetting("Rainbow", false);
    public final NumberSetting rainbowSpeed = new NumberSetting("Rainbow Speed", 48.0f, 1.0f, 240.0f, 1.0f,
            () -> this.rainbowEnabled.getValue());
    public final NumberSetting rainbowSaturation = new NumberSetting("Rainbow Saturation", 82.0f, 0.0f, 100.0f, 1.0f,
            () -> this.rainbowEnabled.getValue());
    public final NumberSetting rainbowBrightness = new NumberSetting("Rainbow Brightness", 100.0f, 10.0f, 100.0f, 1.0f,
            () -> this.rainbowEnabled.getValue());
    public final NumberSetting rainbowOffset = new NumberSetting("Rainbow Offset", 20.0f, 0.0f, 90.0f, 1.0f,
            () -> this.rainbowEnabled.getValue());
    public final BooleanSetting dynamicGradientEnabled = new BooleanSetting("Dynamic Gradient", true,
            () -> this.rainbowEnabled.getValue() || this.textGradientEnabled.getValue());
    public final BooleanSetting gradientAnimationEnabled = new BooleanSetting("Gradient Animation", true,
            () -> this.rainbowEnabled.getValue() || this.textGradientEnabled.getValue());
    public final NumberSetting dynamicGradientSpeed = new NumberSetting("Dynamic Gradient Speed", 48.0f, 0.0f, 240.0f, 1.0f,
            () -> this.gradientAnimationEnabled.getValue() && (this.rainbowEnabled.getValue() || this.textGradientEnabled.getValue()));

    public final BooleanSetting fontGlowEnabled = new BooleanSetting("Font Glow", true);
    public final NumberSetting fontGlowRadius = new NumberSetting("Font Glow Radius", 1.4f, 0.0f, 8.0f, 0.1f,
            () -> this.fontGlowEnabled.getValue());
    public final NumberSetting fontGlowAlpha = new NumberSetting("Font Glow Alpha", 72, 0, 255, 1,
            () -> this.fontGlowEnabled.getValue());
    public final NumberSetting fontGlowQuality = new NumberSetting("Font Glow Quality", 3, 1, 8, 1,
            () -> this.fontGlowEnabled.getValue());

    private final FontRenderer moduleFont = FontPresets.pingfang(16.0f);
    private final SmoothAnimationTimer widthAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer heightAnim = new SmoothAnimationTimer();
    private final Map<Module, AnimatedRow> rowStates = new IdentityHashMap<>();
    private boolean animationReady;

    public ModuleListHud() {
        super("ModuleList");
        this.setX(4.0f);
        this.setY(18.0f);
        this.setWidth(0.0f);
        this.setHeight(0.0f);
        this.setEnabled(true);
    }

    private List<AnimatedRow> updateRows() {
        for (Module module : ZenClient.getInstance().getModuleManager().getModules()) {
            if (module == this || module instanceof Interface || module.getName().isEmpty()) {
                continue;
            }
            AnimatedRow row = this.rowStates.get(module);
            if (module.isEnabled()) {
                if (row == null) {
                    row = new AnimatedRow(module);
                    this.rowStates.put(module, row);
                }
                float textWidth = GlHelper.getStringWidth(module.getName(), this.moduleFont);
                row.updateMetrics(module.getName(), textWidth, this.rowWidth(textWidth));
                row.setTargetVisible(true);
            } else if (row != null) {
                row.setTargetVisible(false);
            }
        }
        this.rowStates.values().forEach(AnimatedRow::tick);
        this.rowStates.values().removeIf(AnimatedRow::isFinishedRemoving);

        List<AnimatedRow> rows = new ArrayList<>(this.rowStates.values());
        rows.sort((a, b) -> Float.compare(b.textWidth, a.textWidth));
        return rows;
    }

    @Override
    public void onRender2D(Render2DEvent event, float x, float y) {
        if (!this.shouldRender()) {
            return;
        }
        List<AnimatedRow> rows = this.updateRows();
        if (rows.isEmpty()) {
            this.setWidth(0.0f);
            this.setHeight(0.0f);
            return;
        }

        float targetWidth = this.measureWidth(rows);
        float targetHeight = this.measureHeight(rows);
        float previousWidth = this.getWidth();
        Alignment anchorBeforeResize = this.resolveAlignment(x, Math.max(previousWidth, targetWidth));
        this.updateSizeAnimation(targetWidth, targetHeight);

        float width = this.widthAnim.getValueF();
        float height = this.heightAnim.getValueF();
        if (anchorBeforeResize == Alignment.RIGHT && !this.isDragging() && previousWidth > 0.0f) {
            this.setX(this.getX() + previousWidth - width);
        }
        this.clampToScreen(width, height);
        this.setWidth(width);
        this.setHeight(height);

        float drawX = this.getX();
        float drawY = this.getY();
        Alignment alignment = this.resolveAlignment(drawX, width);
        Renderer.render(event.guiGraphics(), drawContext -> this.renderRows(drawContext, rows, drawX, drawY, width, alignment));
    }

    private boolean shouldRender() {
        if (!this.isEnabled()) {
            return false;
        }
        Interface interfaceModule = ZenClient.getInstance().getModuleManager().getModule(Interface.class);
        return interfaceModule == null || interfaceModule.isEnabled();
    }

    private float rowWidth(float textWidth) {
        float lineReserve = this.sideLineEnabled.getValue() ? this.settingFloat(this.sideLineWidth) : 0.0f;
        return textWidth + this.settingFloat(this.paddingX) * 2.0f + lineReserve;
    }

    private float measureWidth(List<AnimatedRow> rows) {
        float maxWidth = 0.0f;
        for (AnimatedRow row : rows) {
            maxWidth = Math.max(maxWidth, row.rowWidth);
        }
        return maxWidth;
    }

    private float measureHeight(List<AnimatedRow> rows) {
        float rowHeightValue = this.settingFloat(this.rowHeight);
        float spacing = this.settingFloat(this.rowSpacing);
        float height = 0.0f;
        boolean hasVisibleRow = false;
        for (AnimatedRow row : rows) {
            float progress = row.progress();
            if (progress <= 0.01f) {
                continue;
            }
            if (hasVisibleRow) {
                height += spacing * progress;
            }
            height += rowHeightValue * progress;
            hasVisibleRow = true;
        }
        return height;
    }

    private void updateSizeAnimation(float targetWidth, float targetHeight) {
        if (!this.animationReady) {
            this.widthAnim.setCurrentValue(targetWidth);
            this.widthAnim.setToValue(targetWidth);
            this.heightAnim.setCurrentValue(targetHeight);
            this.heightAnim.setToValue(targetHeight);
            this.animationReady = true;
            return;
        }
        this.widthAnim.animate(targetWidth, 0.18, Easings.EASE_OUT_SINE);
        this.heightAnim.animate(targetHeight, 0.18, Easings.EASE_OUT_SINE);
        this.widthAnim.tick();
        this.heightAnim.tick();
    }

    private void renderRows(DrawContext drawContext, List<AnimatedRow> rows, float x, float y, float width, Alignment alignment) {
        float cursorY = y;
        float rowHeightValue = this.settingFloat(this.rowHeight);
        float spacing = this.settingFloat(this.rowSpacing);
        boolean hasRenderedRow = false;
        for (int i = 0; i < rows.size(); i++) {
            AnimatedRow row = rows.get(i);
            float progress = row.progress();
            if (progress <= 0.01f) {
                continue;
            }
            if (hasRenderedRow) {
                cursorY += spacing * progress;
            }
            float animatedWidth = Math.max(0.1f, row.rowWidth * progress);
            float animatedHeight = Math.max(0.1f, rowHeightValue * progress);
            float rowX = alignment == Alignment.RIGHT ? x + width - animatedWidth : x;
            float slideOffset = (alignment == Alignment.RIGHT ? SLIDE_DISTANCE : -SLIDE_DISTANCE) * (1.0f - progress);
            this.renderRow(drawContext, rows, row, i, rowX + slideOffset, cursorY,
                    animatedWidth, animatedHeight, rowHeightValue, progress, alignment);
            cursorY += rowHeightValue * progress;
            hasRenderedRow = true;
        }
    }

    private void renderRow(DrawContext drawContext, List<AnimatedRow> rows, AnimatedRow row, int index,
                           float x, float y, float width, float height, float fullHeight,
                           float progress, Alignment alignment) {
        float radius = this.settingFloat(this.backgroundRadius);
        RoundedRectangle bounds = RoundedRectangle.ofXYWHR(x, y, width, height, radius);
        int rowColor = this.colorForPosition(index, 0.5f, Math.max(1, rows.size() - 1));

        if (this.backgroundGlowEnabled.getValue()) {
            this.drawBackgroundGlow(drawContext, bounds, rowColor, progress);
        }
        if (this.backgroundBlurEnabled.getValue() && Renderer.isSkikoEnabled()) {
            this.drawBackgroundBlur(drawContext, bounds, progress);
        }
        if (this.backgroundEnabled.getValue()) {
            try (Paint paint = new Paint()) {
                paint.setColor(this.withAlpha(this.settingRgb(this.backgroundColor),
                        Math.round((float)this.settingInt(this.backgroundAlpha) * progress)));
                drawContext.drawRoundedRect(bounds, paint);
            }
        }
        if (this.sideLineEnabled.getValue()) {
            this.drawSideLine(drawContext, bounds, this.withAlpha(rowColor, Math.round(255.0f * progress)), alignment);
        }
        drawContext.save();
        drawContext.clipRoundedRect(bounds, true);
        this.drawModuleName(row.name, x, y, width, fullHeight, index, rows.size(), progress, alignment);
        drawContext.restore();
    }

    private void drawBackgroundBlur(DrawContext drawContext, RoundedRectangle bounds, float progress) {
        float opacity = this.settingFloat(this.blurStrength) * progress;
        LiquidGlassStyle style = LiquidGlassStyle.builder()
                .power(this.settingFloat(this.backgroundRadius))
                .refractionPower(1.0f)
                .refractionStrength(0.0f)
                .noise(0.0f)
                .glow(0.0f, 0.0f)
                .glowEdges(0.0f, 0.85f)
                .blurIterations(2)
                .blurRadius(this.settingFloat(this.blurRadius))
                .blurDownscale(1.0f)
                .opacity(opacity)
                .tint(0x00000000, 0.0f)
                .chromaStrength(0.0f)
                .darkness(0.0f)
                .build();
        drawContext.save();
        drawContext.clipRoundedRect(bounds, true);
        drawContext.drawLiquidGlassPanel(bounds, style);
        drawContext.restore();
    }

    private void drawBackgroundGlow(DrawContext drawContext, RoundedRectangle bounds, int rowColor, float progress) {
        float radius = this.settingFloat(this.glowRadius);
        int iterations = Math.max(1, this.settingInt(this.glowIterations));
        int baseAlpha = Math.round((float)this.settingInt(this.glowAlpha) * progress);
        if (radius <= 0.0f || baseAlpha <= 0) {
            return;
        }
        try (Paint paint = new Paint()) {
            for (int i = iterations; i >= 1; i--) {
                float t = (float)i / (float)iterations;
                float spread = radius * t;
                int alpha = Math.round((float)baseAlpha * (1.0f - t * 0.72f) / (float)iterations);
                paint.setColor(this.withAlpha(rowColor, alpha));
                drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(
                        bounds.x1 - spread,
                        bounds.y1 - spread,
                        bounds.getWidth() + spread * 2.0f,
                        bounds.getHeight() + spread * 2.0f,
                        this.settingFloat(this.backgroundRadius) + spread), paint);
            }
        }
    }

    private void drawSideLine(DrawContext drawContext, RoundedRectangle bounds, int color, Alignment rowAlignment) {
        float lineWidth = this.settingFloat(this.sideLineWidth);
        Alignment lineAlignment = this.resolveLineAlignment(rowAlignment);
        float lineX = lineAlignment == Alignment.RIGHT ? bounds.x2 - lineWidth : bounds.x1;
        try (Paint paint = new Paint()) {
            paint.setColor(color);
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(lineX, bounds.y1, lineWidth, bounds.getHeight(),
                    Math.min(this.settingFloat(this.backgroundRadius), lineWidth)), paint);
        }
    }

    private void drawModuleName(String text, float rowX, float rowY, float rowWidth, float rowHeight,
                                int rowIndex, int rowCount, float alpha, Alignment alignment) {
        float textWidth = GlHelper.getStringWidth(text, this.moduleFont);
        float padding = this.settingFloat(this.paddingX);
        float lineReserve = this.sideLineEnabled.getValue() ? this.settingFloat(this.sideLineWidth) : 0.0f;
        float textX = alignment == Alignment.RIGHT
                ? rowX + rowWidth - padding - textWidth - (this.resolveLineAlignment(alignment) == Alignment.RIGHT ? lineReserve : 0.0f)
                : rowX + padding + (this.resolveLineAlignment(alignment) == Alignment.LEFT ? lineReserve : 0.0f);
        float textY = rowY + (rowHeight - (float)GlHelper.getFontAscent(this.moduleFont)) / 2.0f
                + this.settingFloat(this.paddingY) * 0.25f;
        this.drawColoredText(text, textX, textY, textWidth, rowIndex, rowCount, alpha);
    }

    private void drawColoredText(String text, float x, float y, float textWidth, int rowIndex, int rowCount, float alpha) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float cursorX = x;
        float safeTextWidth = Math.max(1.0f, textWidth);
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            float chWidth = GlHelper.getStringWidth(ch, this.moduleFont);
            float charProgress = Mth.clamp((cursorX - x + chWidth * 0.5f) / safeTextWidth, 0.0f, 1.0f);
            int color = this.colorForPosition(rowIndex, charProgress, Math.max(1, rowCount - 1));
            this.drawGlyphWithGlow(ch, cursorX, y, this.withAlpha(color, Math.round(255.0f * alpha)), alpha);
            cursorX += chWidth;
        }
    }

    private void drawGlyphWithGlow(String text, float x, float y, int color, float rowAlpha) {
        if (this.fontGlowEnabled.getValue()) {
            int glowAlphaValue = this.settingInt(this.fontGlowAlpha);
            float radius = this.settingFloat(this.fontGlowRadius);
            int quality = Math.max(1, this.settingInt(this.fontGlowQuality));
            if (radius > 0.0f && glowAlphaValue > 0) {
                int animatedGlowAlpha = Math.round((float)glowAlphaValue * rowAlpha);
                for (int i = 0; i < quality; i++) {
                    double angle = Math.PI * 2.0 * (double)i / (double)quality;
                    float ox = (float)Math.cos(angle) * radius;
                    float oy = (float)Math.sin(angle) * radius;
                    GlHelper.drawText(text, x + ox, y + oy, this.moduleFont, this.withAlpha(color, animatedGlowAlpha / quality));
                }
            }
        }
        GlHelper.drawText(text, x, y, this.moduleFont, color);
    }

    private int colorForPosition(int rowIndex, float charProgress, int maxRowIndex) {
        if (this.rainbowEnabled.getValue()) {
            float rowOffset = this.settingFloat(this.rainbowOffset);
            float rowProgress = maxRowIndex <= 0 ? 0.0f : (float)rowIndex / (float)maxRowIndex;
            float hueDegrees = this.dynamicRainbowDegrees()
                    + rowProgress * RAINBOW_LIST_SWEEP_DEGREES
                    + (float)rowIndex * rowOffset
                    + charProgress * RAINBOW_TEXT_SWEEP_DEGREES;
            float hue = (hueDegrees % 360.0f) / 360.0f;
            if (hue < 0.0f) {
                hue += 1.0f;
            }
            float saturation = Mth.clamp(this.settingFloat(this.rainbowSaturation) / 100.0f, 0.0f, 1.0f);
            float brightness = Mth.clamp(this.settingFloat(this.rainbowBrightness) / 100.0f, 0.0f, 1.0f);
            return 0xFF000000 | (Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF);
        }
        if (this.textGradientEnabled.getValue()) {
            float rowProgress = maxRowIndex <= 0 ? 0.0f : (float)rowIndex / (float)maxRowIndex;
            float progress = this.dynamicGradientEnabled.getValue()
                    ? (rowProgress + charProgress) * 0.5f
                    : this.gradientMode.is("Horizontal Text") ? charProgress : rowProgress;
            if (this.gradientAnimationEnabled.getValue()) {
                progress = this.cyclicTwoColorProgress(progress + this.dynamicGradientOffset());
            }
            return ColorUtil.interpolateColor(
                    0xFF000000 | this.settingRgb(this.gradientColorStart),
                    0xFF000000 | this.settingRgb(this.gradientColorEnd),
                    progress);
        }
        return 0xFFFFFFFF;
    }

    private float dynamicRainbowDegrees() {
        if (!this.gradientAnimationEnabled.getValue()) {
            return 0.0f;
        }
        return (float)(System.currentTimeMillis() % 60000L) * this.settingFloat(this.rainbowSpeed) / 1000.0f;
    }

    private float dynamicGradientOffset() {
        return (float)(System.currentTimeMillis() % 60000L) * this.settingFloat(this.dynamicGradientSpeed) / 360000.0f;
    }

    private float cyclicTwoColorProgress(float progress) {
        float wrapped = progress - (float)Math.floor(progress);
        return wrapped <= 0.5f ? wrapped * 2.0f : (1.0f - wrapped) * 2.0f;
    }

    private Alignment resolveAlignment(float x, float width) {
        if (this.sideMode.is("Left")) {
            return Alignment.LEFT;
        }
        if (this.sideMode.is("Right")) {
            return Alignment.RIGHT;
        }
        return x + width / 2.0f < (float)mc.getWindow().getGuiScaledWidth() / 2.0f
                ? Alignment.LEFT
                : Alignment.RIGHT;
    }

    private Alignment resolveLineAlignment(Alignment rowAlignment) {
        if (this.sideLineMode.is("Left")) {
            return Alignment.LEFT;
        }
        if (this.sideLineMode.is("Right")) {
            return Alignment.RIGHT;
        }
        return rowAlignment;
    }

    private void clampToScreen(float width, float height) {
        if (mc == null || mc.getWindow() == null) {
            return;
        }
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float maxX = Math.max(MIN_VISIBLE_EDGE, screenWidth - Math.min(width, screenWidth) - MIN_VISIBLE_EDGE);
        float maxY = Math.max(MIN_VISIBLE_EDGE, screenHeight - Math.min(height, screenHeight) - MIN_VISIBLE_EDGE);
        this.setX(Mth.clamp(this.getX(), MIN_VISIBLE_EDGE, maxX));
        this.setY(Mth.clamp(this.getY(), MIN_VISIBLE_EDGE, maxY));
    }

    @Override
    public void mouseDragged(int mouseX, int mouseY) {
        this.setX((float)mouseX - this.getDragOffsetX());
        this.setY((float)mouseY - this.getDragOffsetY());
        this.clampToScreen(Math.max(this.getWidth(), 1.0f), Math.max(this.getHeight(), 1.0f));
    }

    @Override
    public void stopDragging() {
        boolean wasDragging = this.isDragging();
        super.stopDragging();
        if (wasDragging) {
            ConfigManager.saveAllIfReady();
        }
    }

    private float settingFloat(NumberSetting setting) {
        return setting.getValue().floatValue();
    }

    private int settingInt(NumberSetting setting) {
        return Mth.clamp(Math.round(setting.getValue().floatValue()), Math.round(setting.getMin().floatValue()), Math.round(setting.getMax().floatValue()));
    }

    private int settingRgb(NumberSetting setting) {
        return this.settingInt(setting) & 0x00FFFFFF;
    }

    private int withAlpha(int color, int alpha) {
        return Mth.clamp(alpha, 0, 255) << 24 | color & 0x00FFFFFF;
    }

    @Override
    public void onGlRender(GlRenderEvent glRenderEvent, float x, float y) {
    }

    @Override
    public void onSettings() {
    }
}
