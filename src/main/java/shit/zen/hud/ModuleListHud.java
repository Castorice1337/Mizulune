package shit.zen.hud;

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
import shit.zen.modules.impl.render.HUD;
import shit.zen.modules.impl.render.Interface;
import shit.zen.render.DrawContext;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
import shit.zen.render.Paint;
import shit.zen.render.Path;
import shit.zen.render.Rectangle;
import shit.zen.render.Renderer;
import shit.zen.render.RoundedRectangle;
import shit.zen.render.color.ColorContext;
import shit.zen.render.color.ColorProvider;
import shit.zen.render.color.GradientColorProvider;
import shit.zen.render.color.RainbowColorProvider;
import shit.zen.render.color.SolidColorProvider;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.value.MizuColor;
import shit.zen.value.ModeValueGroup;
import shit.zen.value.ToggleValueGroup;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;

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

    private static final class RowRenderLayout {
        private final AnimatedRow row;
        private final int rowIndex;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final float fullHeight;
        private final float progress;
        private float linkHeight;

        private RowRenderLayout(AnimatedRow row, int rowIndex, float x, float y, float width, float height,
                                float fullHeight, float progress) {
            this.row = row;
            this.rowIndex = rowIndex;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.fullHeight = fullHeight;
            this.progress = progress;
        }

        private void linkTo(RowRenderLayout next) {
            this.linkHeight = Math.max(0.0f, next.y - (this.y + this.height));
        }

        private float visualHeight(boolean broken) {
            return this.height + (broken ? 0.0f : this.linkHeight);
        }
    }

    private static final float MIN_VISIBLE_EDGE = 4.0f;
    private static final float DEFAULT_ROW_HEIGHT = 14.0f;
    private static final float DEFAULT_PADDING_X = 4.0f;
    private static final float DEFAULT_PADDING_Y = 1.0f;
    private static final float DEFAULT_ROW_SPACING = 0.0f;
    private static final float DEFAULT_RADIUS = 1.5f;
    private static final float SLIDE_DISTANCE = 18.0f;

    private Value<String> sideMode;
    private Value<Boolean> breakEnabled;
    private Value<Boolean> backgroundEnabled;
    private Value<MizuColor> backgroundColor;
    private Value<Number> backgroundRadius;
    private Value<Number> paddingX;
    private Value<Number> paddingY;
    private Value<Number> rowHeight;
    private Value<Number> rowSpacing;

    private Value<Boolean> backgroundBlurEnabled;
    private Value<Number> blurRadius;
    private Value<Number> blurStrength;

    private Value<Boolean> backgroundGlowEnabled;
    private Value<Number> glowRadius;
    private Value<Number> glowAlpha;
    private Value<Number> glowIterations;

    private Value<Boolean> sideLineEnabled;
    private Value<String> sideLineMode;
    private Value<Number> sideLineWidth;

    private Value<Boolean> useClientColor;
    private ModeValueGroup textColorGroup;
    private Value<MizuColor> solidTextColor;
    private Value<MizuColor> gradientColorStart;
    private Value<MizuColor> gradientColorEnd;
    private Value<String> gradientMode;
    private Value<Boolean> dynamicGradientEnabled;
    private Value<Boolean> gradientAnimationEnabled;
    private Value<Number> dynamicGradientSpeed;
    private Value<Number> rainbowSpeed;
    private Value<Number> rainbowSaturation;
    private Value<Number> rainbowBrightness;
    private Value<Number> rainbowOffset;

    private Value<Boolean> fontGlowEnabled;
    private Value<Number> fontGlowRadius;
    private Value<Number> fontGlowAlpha;
    private Value<Number> fontGlowQuality;

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

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup layout = root.group("layout", "Layout");
        this.sideMode = layout.enumChoice("side_mode", "Side Mode", "Auto", "Auto", "Left", "Right").alias("Side Mode");
        this.breakEnabled = layout.bool("break", "Break", true).alias("Break");
        this.paddingX = layout.decimal("padding_x", "Padding X", DEFAULT_PADDING_X, 0.0f, 12.0f, 0.25f).alias("Padding X");
        this.paddingY = layout.decimal("padding_y", "Padding Y", DEFAULT_PADDING_Y, 0.0f, 8.0f, 0.25f).alias("Padding Y");
        this.rowHeight = layout.decimal("row_height", "Row Height", DEFAULT_ROW_HEIGHT, 9.0f, 24.0f, 0.25f).alias("Row Height");
        this.rowSpacing = layout.decimal("row_spacing", "Row Spacing", DEFAULT_ROW_SPACING, 0.0f, 8.0f, 0.25f).alias("Row Spacing");

        ToggleValueGroup background = root.toggleGroup("background", "Background", true);
        this.backgroundEnabled = background.getEnabledValue().alias("Background");
        this.backgroundColor = background.color("color", "Color", MizuColor.ofArgb(80, 0, 0, 0));
        this.backgroundRadius = background.decimal("radius", "Radius", DEFAULT_RADIUS, 0.0f, 10.0f, 0.25f).alias("Background Radius");

        ToggleValueGroup blur = background.toggleGroup("blur", "Background Blur", false);
        this.backgroundBlurEnabled = blur.getEnabledValue().alias("Background Blur");
        this.blurRadius = blur.decimal("radius", "Blur Radius", 10.0f, 0.0f, 32.0f, 0.5f).alias("Blur Radius");
        this.blurStrength = blur.decimal("strength", "Blur Strength", 0.55f, 0.0f, 1.0f, 0.01f).alias("Blur Strength");

        ToggleValueGroup glow = background.toggleGroup("glow", "Background Glow", true);
        this.backgroundGlowEnabled = glow.getEnabledValue().alias("Background Glow");
        this.glowRadius = glow.decimal("radius", "Glow Radius", 5.0f, 0.0f, 24.0f, 0.5f).alias("Glow Radius");
        this.glowAlpha = glow.integer("alpha", "Glow Alpha", 42, 0, 255, 1).alias("Glow Alpha");
        this.glowIterations = glow.integer("iterations", "Glow Iterations", 3, 1, 8, 1).alias("Glow Iterations");

        ToggleValueGroup sideLine = root.toggleGroup("side_line", "Side Line", true);
        this.sideLineEnabled = sideLine.getEnabledValue().alias("Side Line");
        this.sideLineMode = sideLine.enumChoice("mode", "Mode", "Auto", "Auto", "Left", "Right").alias("Side Line Mode");
        this.sideLineWidth = sideLine.decimal("width", "Width", 2.0f, 0.5f, 5.0f, 0.25f).alias("Side Line Width");

        this.useClientColor = root.bool("use_client_color", "Use Client Color", true).alias("Use Client Color");
        this.textColorGroup = root.modeGroup("text_color", "Text Color", "gradient", "solid", "gradient", "rainbow");
        this.textColorGroup.visibleWhen(() -> !this.useClientColor.getValue());
        this.textColorGroup.getActiveValue().setValue("gradient");
        ValueGroup solid = this.textColorGroup.getModes().get("solid");
        this.solidTextColor = solid.color("color", "Color", MizuColor.ofArgb(255, 255, 255, 255));

        ValueGroup gradient = this.textColorGroup.getModes().get("gradient");
        this.gradientColorStart = gradient.color("start", "Start", MizuColor.ofRgb(0xEA, 0xF7, 0xFF)).alias("Gradient Color Start");
        this.gradientColorEnd = gradient.color("end", "End", MizuColor.ofRgb(0xFF, 0xC4, 0xF1)).alias("Gradient Color End");
        this.gradientMode = gradient.enumChoice("mode", "Mode", "Vertical List", "Vertical List", "Horizontal Text").alias("Gradient Mode");
        this.dynamicGradientEnabled = gradient.bool("dynamic", "Dynamic Gradient", true).alias("Dynamic Gradient");
        this.gradientAnimationEnabled = gradient.bool("animation", "Gradient Animation", true).alias("Gradient Animation");
        this.dynamicGradientSpeed = gradient.decimal("speed", "Dynamic Speed", 48.0f, 0.0f, 240.0f, 1.0f).alias("Dynamic Gradient Speed");

        ValueGroup rainbow = this.textColorGroup.getModes().get("rainbow");
        this.rainbowSpeed = rainbow.decimal("speed", "Speed", 48.0f, 1.0f, 240.0f, 1.0f).alias("Rainbow Speed");
        this.rainbowSaturation = rainbow.decimal("saturation", "Saturation", 82.0f, 0.0f, 100.0f, 1.0f).alias("Rainbow Saturation");
        this.rainbowBrightness = rainbow.decimal("brightness", "Brightness", 100.0f, 10.0f, 100.0f, 1.0f).alias("Rainbow Brightness");
        this.rainbowOffset = rainbow.decimal("offset", "Row Offset", 20.0f, 0.0f, 90.0f, 1.0f).alias("Rainbow Offset");

        ToggleValueGroup fontGlow = root.toggleGroup("font_glow", "Font Glow", true);
        this.fontGlowEnabled = fontGlow.getEnabledValue().alias("Font Glow");
        this.fontGlowRadius = fontGlow.decimal("radius", "Radius", 1.4f, 0.0f, 8.0f, 0.1f).alias("Font Glow Radius");
        this.fontGlowAlpha = fontGlow.integer("alpha", "Alpha", 72, 0, 255, 1).alias("Font Glow Alpha");
        this.fontGlowQuality = fontGlow.integer("quality", "Quality", 3, 1, 8, 1).alias("Font Glow Quality");
    }

    private List<AnimatedRow> updateRows() {
        for (Module module : ZenClient.getInstance().getModuleManager().getModules()) {
            if (module == this || module.getName().isEmpty() || module.isHiddenInModuleList()) {
                this.rowStates.remove(module);
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
        List<RowRenderLayout> layouts = this.computeRowLayouts(rows, x, y, width, alignment);
        boolean broken = this.breakEnabled == null || this.breakEnabled.getValue();
        if (!broken) {
            this.drawConnectedBackgroundBlur(drawContext, layouts, alignment);
        }
        for (RowRenderLayout layout : layouts) {
            this.renderRow(drawContext, rows, layout, broken, alignment);
        }
    }

    private List<RowRenderLayout> computeRowLayouts(List<AnimatedRow> rows, float x, float y, float width, Alignment alignment) {
        List<RowRenderLayout> layouts = new ArrayList<>();
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
            layouts.add(new RowRenderLayout(row, i, rowX + slideOffset, cursorY,
                    animatedWidth, animatedHeight, rowHeightValue, progress));
            cursorY += rowHeightValue * progress;
            hasRenderedRow = true;
        }
        for (int i = 0; i < layouts.size() - 1; i++) {
            layouts.get(i).linkTo(layouts.get(i + 1));
        }
        return layouts;
    }

    private void renderRow(DrawContext drawContext, List<AnimatedRow> rows, RowRenderLayout layout,
                           boolean broken, Alignment alignment) {
        RoundedRectangle bounds = this.rowBounds(layout, broken, alignment);
        int rowColor = this.colorForPosition(layout.rowIndex, 0.5f, Math.max(1, rows.size() - 1));

        if (this.backgroundGlowEnabled.getValue()) {
            this.drawBackgroundGlow(drawContext, bounds, rowColor, layout.progress, broken);
        }
        if (broken && this.backgroundBlurEnabled.getValue() && Renderer.isSkikoEnabled()) {
            this.drawBackgroundBlur(drawContext, bounds, layout.progress);
        }
        if (this.backgroundEnabled.getValue()) {
            try (Paint paint = new Paint()) {
                MizuColor color = this.backgroundColor.getValue();
                paint.setColor(color.withAlpha(Math.round((float)color.alpha() * layout.progress)).toArgb());
                drawContext.drawRoundedRect(bounds, paint);
            }
        }
        if (this.sideLineEnabled.getValue()) {
            this.drawSideLine(drawContext, bounds, this.withAlpha(rowColor, Math.round(255.0f * layout.progress)), alignment, broken);
        }
        drawContext.save();
        drawContext.clipRoundedRect(this.textClipBounds(layout, bounds, broken), true);
        this.drawModuleName(layout.row.name, layout.x, layout.y, layout.width, layout.fullHeight,
                layout.rowIndex, rows.size(), layout.progress, alignment);
        drawContext.restore();
    }

    private RoundedRectangle rowBounds(RowRenderLayout layout, boolean broken, Alignment alignment) {
        float radius = this.settingFloat(this.backgroundRadius);
        if (broken) {
            return RoundedRectangle.ofXYWHR(layout.x, layout.y, layout.width, layout.height, radius);
        }
        if (alignment == Alignment.RIGHT) {
            return RoundedRectangle.ofXYWHRadii(layout.x, layout.y, layout.width, layout.visualHeight(false),
                    new float[]{0.0f, 0.0f, 0.0f, radius});
        }
        return RoundedRectangle.ofXYWHRadii(layout.x, layout.y, layout.width, layout.visualHeight(false),
                new float[]{0.0f, 0.0f, radius, 0.0f});
    }

    private RoundedRectangle textClipBounds(RowRenderLayout layout, RoundedRectangle visualBounds, boolean broken) {
        if (broken) {
            return visualBounds;
        }
        return RoundedRectangle.ofXYWHR(layout.x, layout.y, layout.width, layout.height, 0.0f);
    }

    private void drawBackgroundBlur(DrawContext drawContext, RoundedRectangle bounds, float progress) {
        float opacity = this.settingFloat(this.blurStrength) * progress;
        if (opacity <= 0.001f) {
            return;
        }
        drawContext.drawBackdropBlurredRoundedRect(bounds, this.settingFloat(this.blurRadius), opacity, 0x00000000);
    }

    private void drawConnectedBackgroundBlur(DrawContext drawContext, List<RowRenderLayout> layouts, Alignment alignment) {
        if (!this.backgroundBlurEnabled.getValue() || !Renderer.isSkikoEnabled() || layouts.isEmpty()) {
            return;
        }
        float opacity = this.settingFloat(this.blurStrength) * this.maxRowProgress(layouts);
        if (opacity <= 0.001f) {
            return;
        }
        try (Path path = new Path()) {
            Rectangle bounds = this.addConnectedBlurPath(path, layouts, alignment);
            if (bounds != null) {
                drawContext.drawBackdropBlurredPath(path, bounds, this.settingFloat(this.blurRadius), opacity, 0x00000000);
            }
        }
    }

    private Rectangle addConnectedBlurPath(Path path, List<RowRenderLayout> layouts, Alignment alignment) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (RowRenderLayout layout : layouts) {
            RoundedRectangle bounds = this.rowBounds(layout, false, alignment);
            if (bounds.getWidth() <= 0.0f || bounds.getHeight() <= 0.0f) {
                continue;
            }
            path.addRoundedRect(bounds);
            minX = Math.min(minX, bounds.x1);
            minY = Math.min(minY, bounds.y1);
            maxX = Math.max(maxX, bounds.x2);
            maxY = Math.max(maxY, bounds.y2);
        }
        if (!Float.isFinite(minX) || maxX <= minX || maxY <= minY) {
            return null;
        }
        return Rectangle.ofCorners(minX, minY, maxX, maxY);
    }

    private float maxRowProgress(List<RowRenderLayout> layouts) {
        float progress = 0.0f;
        for (RowRenderLayout layout : layouts) {
            progress = Math.max(progress, layout.progress);
        }
        return progress;
    }

    private void drawBackgroundGlow(DrawContext drawContext, RoundedRectangle bounds, int rowColor, float progress, boolean broken) {
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
                drawContext.drawRoundedRect(this.expandedGlowBounds(bounds, spread, broken), paint);
            }
        }
    }

    private RoundedRectangle expandedGlowBounds(RoundedRectangle bounds, float spread, boolean broken) {
        if (broken) {
            return RoundedRectangle.ofXYWHR(
                    bounds.x1 - spread,
                    bounds.y1 - spread,
                    bounds.getWidth() + spread * 2.0f,
                    bounds.getHeight() + spread * 2.0f,
                    this.settingFloat(this.backgroundRadius) + spread);
        }
        return RoundedRectangle.ofXYWHRadii(
                bounds.x1 - spread,
                bounds.y1 - spread,
                bounds.getWidth() + spread * 2.0f,
                bounds.getHeight() + spread * 2.0f,
                new float[]{
                        this.expandedCornerRadius(bounds.topLeftRadius, spread),
                        this.expandedCornerRadius(bounds.topRightRadius, spread),
                        this.expandedCornerRadius(bounds.bottomRightRadius, spread),
                        this.expandedCornerRadius(bounds.bottomLeftRadius, spread)
                });
    }

    private float expandedCornerRadius(float radius, float spread) {
        return radius > 0.0f ? radius + spread : 0.0f;
    }

    private void drawSideLine(DrawContext drawContext, RoundedRectangle bounds, int color, Alignment rowAlignment, boolean broken) {
        float lineWidth = this.settingFloat(this.sideLineWidth);
        Alignment lineAlignment = this.resolveLineAlignment(rowAlignment);
        float lineX = lineAlignment == Alignment.RIGHT ? bounds.x2 - lineWidth : bounds.x1;
        try (Paint paint = new Paint()) {
            paint.setColor(color);
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(lineX, bounds.y1, lineWidth, bounds.getHeight(),
                    broken ? Math.min(this.settingFloat(this.backgroundRadius), lineWidth) : 0.0f), paint);
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
        return this.textColorProvider().color(new ColorContext(System.currentTimeMillis(), rowIndex, maxRowIndex, charProgress));
    }

    private Alignment resolveAlignment(float x, float width) {
        if (this.isValue(this.sideMode, "Left")) {
            return Alignment.LEFT;
        }
        if (this.isValue(this.sideMode, "Right")) {
            return Alignment.RIGHT;
        }
        return x + width / 2.0f < (float)mc.getWindow().getGuiScaledWidth() / 2.0f
                ? Alignment.LEFT
                : Alignment.RIGHT;
    }

    private Alignment resolveLineAlignment(Alignment rowAlignment) {
        if (this.isValue(this.sideLineMode, "Left")) {
            return Alignment.LEFT;
        }
        if (this.isValue(this.sideLineMode, "Right")) {
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

    private ColorProvider textColorProvider() {
        if (this.useClientColor != null && this.useClientColor.getValue()) {
            return new GradientColorProvider(
                    HUD.clientColorStart(),
                    HUD.clientColorEnd(),
                    "Vertical List",
                    true,
                    true,
                    this.settingFloat(this.dynamicGradientSpeed));
        }
        String mode = this.textColorGroup.getActiveModeId();
        if ("rainbow".equals(mode)) {
            return new RainbowColorProvider(true,
                    this.settingFloat(this.rainbowSpeed),
                    this.settingFloat(this.rainbowSaturation),
                    this.settingFloat(this.rainbowBrightness),
                    this.settingFloat(this.rainbowOffset));
        }
        if ("solid".equals(mode)) {
            return new SolidColorProvider(this.solidTextColor.getValue());
        }
        return new GradientColorProvider(
                this.gradientColorStart.getValue(),
                this.gradientColorEnd.getValue(),
                this.gradientMode.getValue(),
                this.dynamicGradientEnabled.getValue(),
                this.gradientAnimationEnabled.getValue(),
                this.settingFloat(this.dynamicGradientSpeed));
    }

    private boolean isValue(Value<String> value, String expected) {
        return value != null && expected.equalsIgnoreCase(value.getValue());
    }

    private float settingFloat(Value<Number> setting) {
        return setting.getValue().floatValue();
    }

    private int settingInt(Value<Number> setting) {
        int value = Math.round(setting.getValue().floatValue());
        Object min = setting.getMetadata().get("min");
        Object max = setting.getMetadata().get("max");
        int minValue = min instanceof Number number ? Math.round(number.floatValue()) : Integer.MIN_VALUE;
        int maxValue = max instanceof Number number ? Math.round(number.floatValue()) : Integer.MAX_VALUE;
        return Mth.clamp(value, minValue, maxValue);
    }

    private int withAlpha(int color, int alpha) {
        return Mth.clamp(alpha, 0, 255) << 24 | color & 0x00FFFFFF;
    }

    @Override
    public void onGlRender(GlRenderEvent glRenderEvent, float x, float y) {
    }

}
