package shit.zen.hud;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.GlRenderEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.render.DrawContext;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
import shit.zen.render.Paint;
import shit.zen.render.Path;
import shit.zen.render.RoundedRectangle;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.utils.misc.Assets;
import shit.zen.utils.render.Argb;
import shit.zen.value.MizuColor;
import shit.zen.value.ToggleValueGroup;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;

public class PotionEffectsHud extends HudElement {
    private static final float DEFAULT_SCALE = 1.0f;
    private static final float DEFAULT_MIN_WIDTH = 102.0f;
    private static final float MAX_EFFECTIVE_MIN_WIDTH = 170.0f;
    private static final float DEFAULT_ROW_HEIGHT = 26.5f;
    private static final float DEFAULT_ROW_SPACING = 4.5f;
    private static final float DEFAULT_RADIUS = 12.5f;
    private static final float DEFAULT_PADDING_X = 8.0f;
    private static final float DEFAULT_TEXT_GAP = 7.0f;
    private static final float DEFAULT_RING_SIZE = 21.0f;
    private static final float DEFAULT_RING_THICKNESS = 2.5f;
    private static final float DEFAULT_ICON_SIZE = 11.5f;
    private static final float OLD_MIN_WIDTH = 100.0f;
    private static final float OLD_ROW_HEIGHT = 18.0f;
    private static final float OLD_ROW_SPACING = 2.0f;
    private static final float OLD_ICON_BOX_WIDTH = 20.0f;
    private static final float OLD_PADDING = 5.0f;
    private static final float OLD_RADIUS = 4.5f;

    private static final MizuColor DEFAULT_BACKGROUND_TOP = MizuColor.ofArgb(104, 12, 14, 18);
    private static final MizuColor DEFAULT_BACKGROUND_BOTTOM = MizuColor.ofArgb(132, 5, 7, 10);
    private static final MizuColor DEFAULT_TEXT = MizuColor.ofArgb(236, 255, 255, 255);
    private static final MizuColor DEFAULT_DURATION = MizuColor.ofArgb(224, 244, 247, 255);

    private final List<EffectEntry> effectEntryList = new ArrayList<>();
    private final EffectIconLibrary iconLibrary = new EffectIconLibrary();

    private final FontRenderer effectNameFont = FontPresets.pingfang(16.5f);
    private final FontRenderer durationFont = FontPresets.axiformaBold(15.0f);
    private final FontRenderer oldEffectNameFont = FontPresets.pingfang(16.0f);
    private final FontRenderer oldTimerFont = FontPresets.axiformaBold(16.0f);
    private final FontRenderer oldDurationFont = FontPresets.axiformaBold(14.0f);

    private final Paint backgroundPaint = new Paint();
    private final Paint surfacePaint = new Paint();
    private final Paint ringPaint = new Paint();
    private final Paint iconPaint = new Paint();
    private final Paint textPaint = new Paint();

    private Value<String> style;
    private Value<Number> scale;
    private Value<Number> minWidth;
    private Value<Number> rowHeight;
    private Value<Number> rowSpacing;
    private Value<Number> radius;
    private Value<Number> paddingX;
    private Value<Number> textGap;

    private Value<Boolean> backgroundEnabled;
    private Value<MizuColor> backgroundTopColor;
    private Value<MizuColor> backgroundBottomColor;

    private Value<Boolean> backgroundBlurEnabled;
    private Value<Number> backgroundBlurRadius;
    private Value<Number> backgroundBlurOpacity;

    private Value<Boolean> shadowEnabled;
    private Value<Number> shadowAlpha;
    private Value<Number> shadowBlur;
    private Value<Number> shadowSpread;
    private Value<Number> shadowOffset;

    private Value<Boolean> ringEnabled;
    private Value<Number> ringSize;
    private Value<Number> ringThickness;
    private Value<Number> ringTrackAlpha;
    private Value<Number> ringGlowAlpha;
    private Value<Number> ringGlowRadius;

    private Value<Boolean> iconEnabled;
    private Value<Number> iconSize;
    private Value<Number> iconOpacity;

    private Value<Boolean> useEffectTextColor;
    private Value<MizuColor> textColor;
    private Value<MizuColor> durationColor;

    public PotionEffectsHud() {
        super("Effects");
        this.setX(10.0f);
        this.setY(50.0f);
        this.setWidth(DEFAULT_MIN_WIDTH);
        this.setHeight(DEFAULT_ROW_HEIGHT);
        this.setEnabled(true);

        this.backgroundPaint.setAntialias(true);
        this.surfacePaint.setAntialias(true);
        this.ringPaint.setAntialias(true);
        this.iconPaint.setAntialias(true);
        this.textPaint.setAntialias(true);
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup layout = root.group("layout", "Layout");
        this.style = layout.enumChoice("style", "Style", "Modern", "Modern", "Old").alias("Mode");
        this.scale = layout.decimal("scale", "Scale", DEFAULT_SCALE, 0.60, 1.80, 0.02).alias("Size");
        this.minWidth = layout.decimal("min_width", "Min Width", DEFAULT_MIN_WIDTH, 82.0, 190.0, 1.0).alias("Min Width");
        this.rowHeight = layout.decimal("row_height", "Row Height", DEFAULT_ROW_HEIGHT, 22.0, 54.0, 0.5).alias("Row Height");
        this.rowSpacing = layout.decimal("row_spacing", "Row Spacing", DEFAULT_ROW_SPACING, 0.0, 12.0, 0.5).alias("Row Spacing");
        this.radius = layout.decimal("radius", "Radius", DEFAULT_RADIUS, 4.0, 24.0, 0.5).alias("Background Radius");
        this.paddingX = layout.decimal("padding_x", "Padding X", DEFAULT_PADDING_X, 5.0, 16.0, 0.5).alias("Padding X");
        this.textGap = layout.decimal("text_gap", "Text Gap", DEFAULT_TEXT_GAP, 4.0, 16.0, 0.5).alias("Text Gap");

        ToggleValueGroup background = root.toggleGroup("background", "Background", true);
        this.backgroundEnabled = background.getEnabledValue().alias("Background");
        this.backgroundTopColor = background.color("top", "Top", DEFAULT_BACKGROUND_TOP);
        this.backgroundBottomColor = background.color("bottom", "Bottom", DEFAULT_BACKGROUND_BOTTOM);

        ToggleValueGroup backgroundBlur = background.toggleGroup("blur", "Background Blur", true);
        this.backgroundBlurEnabled = backgroundBlur.getEnabledValue().alias("Background Blur");
        this.backgroundBlurRadius = backgroundBlur.decimal("radius", "Blur Radius", 6.0f, 0.0f, 22.0f, 0.5f)
                .alias("Blur Radius");
        this.backgroundBlurOpacity = backgroundBlur.decimal("opacity", "Blur Opacity", 0.62f, 0.0f, 1.0f, 0.01f)
                .alias("Blur Opacity");

        ToggleValueGroup shadow = root.toggleGroup("shadow", "Shadow", true);
        this.shadowEnabled = shadow.getEnabledValue().alias("Shadow");
        this.shadowAlpha = shadow.integer("alpha", "Alpha", 128, 0, 255, 1).alias("Shadow Alpha");
        this.shadowBlur = shadow.decimal("blur", "Blur", 6.6f, 0.0f, 22.0f, 0.25f).alias("Shadow Blur");
        this.shadowSpread = shadow.decimal("spread", "Spread", 2.0f, 0.0f, 8.0f, 0.25f).alias("Shadow Spread");
        this.shadowOffset = shadow.decimal("offset_y", "Offset Y", 2.2f, 0.0f, 8.0f, 0.25f).alias("Shadow Offset Y");

        ToggleValueGroup ring = root.toggleGroup("progress_ring", "Progress Ring", true);
        this.ringEnabled = ring.getEnabledValue().alias("Progress Ring");
        this.ringSize = ring.decimal("size", "Size", DEFAULT_RING_SIZE, 14.0f, 34.0f, 0.5f).alias("Ring Size");
        this.ringThickness = ring.decimal("thickness", "Thickness", DEFAULT_RING_THICKNESS, 1.0f, 5.0f, 0.1f)
                .alias("Ring Thickness");
        this.ringTrackAlpha = ring.integer("track_alpha", "Track Alpha", 38, 0, 160, 1).alias("Ring Track Alpha");
        this.ringGlowAlpha = ring.integer("glow_alpha", "Glow Alpha", 124, 0, 255, 1).alias("Ring Glow Alpha");
        this.ringGlowRadius = ring.decimal("glow_radius", "Glow Radius", 3.2f, 0.0f, 12.0f, 0.2f)
                .alias("Ring Glow Radius");

        ToggleValueGroup icon = root.toggleGroup("icon", "Icon", true);
        this.iconEnabled = icon.getEnabledValue().alias("Icon");
        this.iconSize = icon.decimal("size", "Size", DEFAULT_ICON_SIZE, 7.0f, 22.0f, 0.5f).alias("Icon Size");
        this.iconOpacity = icon.decimal("opacity", "Opacity", 0.96f, 0.0f, 1.0f, 0.01f).alias("Icon Opacity");

        ValueGroup text = root.group("text", "Text");
        this.useEffectTextColor = text.bool("use_effect_color", "Use Effect Color", true).alias("Effect Text Color");
        this.textColor = text.color("name", "Name", DEFAULT_TEXT);
        this.durationColor = text.color("duration", "Duration", DEFAULT_DURATION);
    }

    @Override
    public void onEnable() {
        this.effectEntryList.clear();
    }

    @EventTarget
    public void onTick(TickEvent tickEvent) {
        if (mc.player == null) {
            this.effectEntryList.forEach(EffectEntry::startRemove);
            return;
        }

        Collection<MobEffectInstance> activeEffects = mc.player.getActiveEffects();
        this.effectEntryList.stream()
                .filter(entry -> activeEffects.stream().noneMatch(effect -> effect.getEffect() == entry.getEffect()))
                .forEach(EffectEntry::startRemove);

        for (MobEffectInstance effectInstance : activeEffects) {
            Optional<EffectEntry> existing = this.effectEntryList.stream()
                    .filter(entry -> !entry.removing && entry.getEffect() == effectInstance.getEffect())
                    .findFirst();
            if (existing.isPresent()) {
                existing.get().updateEffect(effectInstance);
            } else {
                this.effectEntryList.add(new EffectEntry(this, effectInstance));
            }
        }

        this.effectEntryList.sort((a, b) -> Float.compare(this.measureEntryWidth(b), this.measureEntryWidth(a)));
    }

    @Override
    public void onGlRender(GlRenderEvent event, float x, float y) {
        if (!this.isEnabled()) {
            return;
        }
        this.renderEffects(event.drawContext(), x, y);
    }

    @Override
    public void onRender2D(Render2DEvent event, float x, float y) {
    }

    private void renderEffects(DrawContext ctx, float x, float y) {
        if (ctx == null) {
            return;
        }

        this.effectEntryList.removeIf(EffectEntry::isRemoveDone);
        if (this.effectEntryList.isEmpty()) {
            this.setWidth(0.0f);
            this.setHeight(0.0f);
            return;
        }

        boolean modernStyle = this.isModernStyle();
        float scaleValue = this.number(this.scale, DEFAULT_SCALE);
        float rowHeightValue = modernStyle ? this.number(this.rowHeight, DEFAULT_ROW_HEIGHT) : OLD_ROW_HEIGHT;
        float spacingValue = modernStyle ? this.number(this.rowSpacing, DEFAULT_ROW_SPACING) : OLD_ROW_SPACING;
        float width = this.measureHudWidth();
        float cursorY = 0.0f;

        ctx.save();
        ctx.translate(x, y);
        ctx.scale(scaleValue, scaleValue);

        for (EffectEntry entry : this.effectEntryList) {
            entry.tick(rowHeightValue);
            entry.show(rowHeightValue);

            if (!entry.positioned) {
                entry.yAnim.setCurrentValue(cursorY);
                entry.yAnim.setToValue(cursorY);
                entry.positioned = true;
            }
            entry.yAnim.animate(cursorY, 0.18, Easings.EASE_OUT_SINE);

            float entryHeight = entry.heightAnim.getValueF();
            float rowAlpha = entry.alphaAnim.getValueF();
            float entryY = entry.yAnim.getValueF();

            if (entryHeight > 0.01f && rowAlpha > 0.01f) {
                if (modernStyle) {
                    this.drawRow(ctx, entry, 0.0f, entryY, width, entryHeight, rowHeightValue, rowAlpha);
                } else {
                    this.drawOldRow(ctx, entry, 0.0f, entryY, width, entryHeight, rowAlpha);
                }
            }

            cursorY += entryHeight;
            if (entryHeight > 0.01f) {
                cursorY += spacingValue;
            }
        }

        ctx.restore();

        float hudHeight = Math.max(0.0f, cursorY - spacingValue);
        this.setWidth(width * scaleValue);
        this.setHeight(hudHeight * scaleValue);
    }

    private void drawOldRow(DrawContext ctx, EffectEntry entry, float x, float y,
                            float width, float height, float alpha) {
        int effectColor = this.getRawEffectColor(entry.effectInstance.getEffect());
        float barWidth = Math.max(0.0f, width - OLD_ICON_BOX_WIDTH);
        float barX = x + OLD_ICON_BOX_WIDTH;
        float durationPct = this.durationProgress(entry);

        this.backgroundPaint.setGradCoords(null);
        this.backgroundPaint.setLinGradient(null);
        this.backgroundPaint.setShader(null);
        this.backgroundPaint.setMaskFilter(null);
        this.backgroundPaint.setStrokeCap(Paint.StrokeCap.FILL);

        this.backgroundPaint.setColor(Argb.fromRgbaComponents(30, 30, 35, Math.round(80.0f * alpha)));
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHRadii(
                barX,
                y,
                barWidth,
                height,
                new float[]{0.0f, 0.0f, OLD_RADIUS, OLD_RADIUS, OLD_RADIUS, OLD_RADIUS, 0.0f, 0.0f}
        ), this.backgroundPaint);

        this.surfacePaint.setGradCoords(null);
        this.surfacePaint.setLinGradient(null);
        this.surfacePaint.setShader(null);
        this.surfacePaint.setMaskFilter(null);
        this.surfacePaint.setStrokeCap(Paint.StrokeCap.FILL);
        this.surfacePaint.setColor(Argb.withAlpha(effectColor, Math.round(140.0f * alpha)));
        if (durationPct > 0.0f) {
            ctx.drawRoundedRect(RoundedRectangle.ofXYWHRadii(
                    barX,
                    y,
                    barWidth * durationPct,
                    height,
                    new float[]{0.0f, 0.0f, OLD_RADIUS * durationPct, OLD_RADIUS * durationPct,
                            OLD_RADIUS * durationPct, OLD_RADIUS * durationPct, 0.0f, 0.0f}
            ), this.surfacePaint);
        }

        int red = effectColor >> 16 & 0xFF;
        int green = effectColor >> 8 & 0xFF;
        int blue = effectColor & 0xFF;
        this.backgroundPaint.setColor(Argb.fromRgbaComponents(
                Math.round(red * 0.7f + 76.5f),
                Math.round(green * 0.7f + 76.5f),
                Math.round(blue * 0.7f + 76.5f),
                Math.round(160.0f * alpha)
        ));
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHRadii(
                x,
                y,
                OLD_ICON_BOX_WIDTH,
                height,
                new float[]{OLD_RADIUS, OLD_RADIUS, 0.0f, 0.0f, 0.0f, 0.0f, OLD_RADIUS, OLD_RADIUS}
        ), this.backgroundPaint);

        int textColor = Argb.withAlpha(0xFFFFFFFF, Math.round(185.0f * alpha));
        this.drawOldText(ctx, entry, x, y, width, height, textColor);
    }

    private void drawOldText(DrawContext ctx, EffectEntry entry, float x, float y,
                             float width, float height, int color) {
        String amplifierText = this.formatAmplifier(entry.effectInstance.getAmplifier() + 1);
        float textY = y + (height - GlHelper.getFontAscent(this.oldEffectNameFont)) * 0.5f;
        float ampWidth = GlHelper.getStringWidth(amplifierText, this.oldTimerFont);
        float durationWidth = GlHelper.getStringWidth(entry.durationText, this.oldDurationFont);

        this.textPaint.setGradCoords(null);
        this.textPaint.setLinGradient(null);
        this.textPaint.setShader(null);
        this.textPaint.setMaskFilter(null);
        this.textPaint.setStrokeCap(Paint.StrokeCap.FILL);
        this.textPaint.setColor(color);

        GlHelper.drawTextFormatted(amplifierText,
                x + (OLD_ICON_BOX_WIDTH - ampWidth) * 0.5f,
                textY,
                this.oldTimerFont,
                this.textPaint,
                false);
        GlHelper.drawTextFormatted(entry.effectName,
                x + OLD_ICON_BOX_WIDTH + OLD_PADDING,
                textY,
                this.oldEffectNameFont,
                this.textPaint,
                false);
        GlHelper.drawTextFormatted(entry.durationText,
                x + width - durationWidth - OLD_PADDING,
                textY + 1.0f,
                this.oldDurationFont,
                this.textPaint,
                false);
    }

    private void drawRow(DrawContext ctx, EffectEntry entry, float x, float y, float width,
                         float height, float fullHeight, float alpha) {
        float radiusValue = Math.min(this.number(this.radius, DEFAULT_RADIUS), height * 0.5f);
        RoundedRectangle rowRect = RoundedRectangle.ofXYWHR(x, y, width, height, radiusValue);
        int effectColor = this.getEffectColor(entry.effectInstance.getEffect());

        this.drawRowSurface(ctx, rowRect, effectColor, alpha);

        float padding = this.number(this.paddingX, DEFAULT_PADDING_X);
        float ringSizeValue = Math.min(this.number(this.ringSize, DEFAULT_RING_SIZE), Math.max(12.0f, fullHeight - 4.0f));
        float ringX = x + padding;
        float ringY = y + (height - ringSizeValue) * 0.5f;
        float ringCenterX = ringX + ringSizeValue * 0.5f;
        float ringCenterY = ringY + ringSizeValue * 0.5f;

        if (this.bool(this.ringEnabled, true)) {
            this.drawProgressRing(ctx, entry, ringCenterX, ringCenterY, ringSizeValue, effectColor, alpha);
        }

        if (this.bool(this.iconEnabled, true)) {
            this.drawEffectIcon(ctx, entry.effectInstance.getEffect(), ringCenterX, ringCenterY, effectColor, alpha);
        }

        this.drawRowText(ctx, entry, x, y, width, height, ringX + ringSizeValue, effectColor, alpha);
    }

    private void drawRowSurface(DrawContext ctx, RoundedRectangle rowRect, int effectColor, float alpha) {
        if (this.bool(this.shadowEnabled, true)) {
            int shadow = Argb.withAlpha(0xFF000000, Math.round(this.integer(this.shadowAlpha, 128) * alpha));
            float blur = this.number(this.shadowBlur, 6.6f);
            float spread = this.number(this.shadowSpread, 2.0f);
            float offsetY = this.number(this.shadowOffset, 2.2f);
            ctx.drawBlurredRoundedRect(rowRect, 0.0f, offsetY, blur, spread, shadow);

            int colorShadow = Argb.scaleAlpha(effectColor, 0.18f * alpha);
            ctx.drawBlurredRoundedRect(rowRect, 0.0f, Math.max(0.8f, offsetY * 0.25f),
                    Math.max(3.0f, blur * 0.55f), Math.max(0.7f, spread * 0.35f), colorShadow);
        }

        if (this.bool(this.backgroundBlurEnabled, true)) {
            ctx.drawBackdropBlurredRoundedRect(
                    rowRect,
                    this.number(this.backgroundBlurRadius, 6.0f),
                    this.number(this.backgroundBlurOpacity, 0.62f) * alpha,
                    0x00000000
            );
        }

        if (this.bool(this.backgroundEnabled, true)) {
            int top = Argb.scaleAlpha(this.color(this.backgroundTopColor, DEFAULT_BACKGROUND_TOP).toArgb(), alpha);
            int bottom = Argb.scaleAlpha(this.color(this.backgroundBottomColor, DEFAULT_BACKGROUND_BOTTOM).toArgb(), alpha);

            this.backgroundPaint.setColor(0xFFFFFFFF);
            this.backgroundPaint.setStrokeCap(Paint.StrokeCap.FILL);
            this.backgroundPaint.setMaskFilter(null);
            this.backgroundPaint.setLinGradient(null);
            this.backgroundPaint.setShader(null);
            this.backgroundPaint.setGradCoords(new Paint.GradientCoords(
                    rowRect.x1,
                    rowRect.y1,
                    rowRect.x1,
                    rowRect.y2,
                    top,
                    bottom
            ));
            ctx.drawRoundedRect(rowRect, this.backgroundPaint);
        }

        this.surfacePaint.setGradCoords(null);
        this.surfacePaint.setMaskFilter(null);
        this.surfacePaint.setLinGradient(null);
        this.surfacePaint.setShader(null);
        this.surfacePaint.setStrokeCap(Paint.StrokeCap.FILL);
        this.surfacePaint.setColor(Argb.withAlpha(0xFFFFFFFF, Math.round(10.0f * alpha)));
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(
                rowRect.x1 + rowRect.topLeftRadius * 0.75f,
                rowRect.y1 + 1.1f,
                Math.max(0.0f, rowRect.getWidth() - rowRect.topLeftRadius * 1.5f),
                0.7f,
                0.35f
        ), this.surfacePaint);
    }

    private void drawProgressRing(DrawContext ctx, EffectEntry entry, float centerX, float centerY,
                                  float size, int effectColor, float alpha) {
        float thickness = Math.min(this.number(this.ringThickness, DEFAULT_RING_THICKNESS), size * 0.28f);
        float radius = Math.max(2.0f, size * 0.5f - thickness * 0.5f);
        float progress = this.durationProgress(entry);
        float left = centerX - radius;
        float top = centerY - radius;
        float right = centerX + radius;
        float bottom = centerY + radius;

        this.ringPaint.setGradCoords(null);
        this.ringPaint.setLinGradient(null);
        this.ringPaint.setShader(null);
        this.ringPaint.setStrokeCap(Paint.StrokeCap.STROKE);
        this.ringPaint.setStrokeJoin(Paint.StrokeJoin.ROUND);
        this.ringPaint.setStrokeWidth(thickness);

        float glowRadius = this.number(this.ringGlowRadius, 3.2f);
        int glowAlpha = Math.round(this.integer(this.ringGlowAlpha, 124) * alpha);
        if (glowRadius > 0.01f && glowAlpha > 0) {
            this.ringPaint.setMaskFilter(new Paint.BlurMaskFilter(glowRadius));
            this.ringPaint.setColor(Argb.withAlpha(effectColor, glowAlpha));
            ctx.drawArc(left, top, right, bottom, -90.0f, Math.max(16.0f, 360.0f * progress), false, this.ringPaint);
            this.ringPaint.setMaskFilter(null);
        }

        int trackAlpha = Math.round(this.integer(this.ringTrackAlpha, 38) * alpha);
        if (trackAlpha > 0) {
            this.ringPaint.setColor(Argb.withAlpha(0xFFFFFFFF, trackAlpha));
            ctx.drawArc(left, top, right, bottom, -90.0f, 360.0f, false, this.ringPaint);
        }

        this.ringPaint.setColor(Argb.withAlpha(effectColor, Math.round(238.0f * alpha)));
        ctx.drawArc(left, top, right, bottom, -90.0f, 360.0f * progress, false, this.ringPaint);
        this.ringPaint.setStrokeCap(Paint.StrokeCap.FILL);
    }

    private void drawEffectIcon(DrawContext ctx, MobEffect effect, float centerX, float centerY, int effectColor, float alpha) {
        float size = this.number(this.iconSize, DEFAULT_ICON_SIZE);
        float opacity = this.number(this.iconOpacity, 0.96f);
        float x = centerX - size * 0.5f;
        float y = centerY - size * 0.5f;

        this.iconPaint.setGradCoords(null);
        this.iconPaint.setLinGradient(null);
        this.iconPaint.setShader(null);
        this.iconPaint.setMaskFilter(null);
        this.iconPaint.setStrokeCap(Paint.StrokeCap.FILL);
        this.iconPaint.setColor(Argb.withAlpha(effectColor, Math.round(255.0f * Mth.clamp(alpha * opacity, 0.0f, 1.0f))));

        if (!this.iconLibrary.draw(ctx, this.iconFor(effect), x, y, size, this.iconPaint)) {
            ctx.drawArc(x + size * 0.22f, y + size * 0.22f, x + size * 0.78f, y + size * 0.78f,
                    0.0f, 360.0f, false, this.iconPaint);
        }
    }

    private void drawRowText(DrawContext ctx, EffectEntry entry, float x, float y, float width,
                             float height, float iconRight, int effectColor, float alpha) {
        float padding = this.number(this.paddingX, DEFAULT_PADDING_X);
        float gap = this.number(this.textGap, DEFAULT_TEXT_GAP);
        float centerY = y + height * 0.5f;
        float nameX = iconRight + gap;
        float durationWidth = GlHelper.getStringWidth(entry.durationText, this.durationFont);
        float durationX = x + width - padding - durationWidth;
        int nameColor = this.bool(this.useEffectTextColor, true)
                ? Argb.withAlpha(effectColor, Math.round(244.0f * alpha))
                : Argb.scaleAlpha(this.color(this.textColor, DEFAULT_TEXT).toArgb(), alpha);
        int timeColor = Argb.scaleAlpha(this.color(this.durationColor, DEFAULT_DURATION).toArgb(), alpha);

        this.drawString(ctx, entry.titleText, nameX, centerY, this.effectNameFont, nameColor, 0.4f);
        this.drawString(ctx, entry.durationText, durationX, centerY + 0.3f, this.durationFont, timeColor, 0.35f);
    }

    private void drawString(DrawContext ctx, String text, float x, float centerY, FontRenderer font, int color, float shadowOffset) {
        if (text == null || text.isEmpty()) {
            return;
        }

        float ascent = GlHelper.getFontAscent(font);
        float capHeight = font.getMetrics().capHeight();
        float drawY = centerY - capHeight * 0.5f - (ascent - capHeight);

        this.textPaint.setGradCoords(null);
        this.textPaint.setLinGradient(null);
        this.textPaint.setShader(null);
        this.textPaint.setMaskFilter(null);
        this.textPaint.setStrokeCap(Paint.StrokeCap.FILL);

        if (shadowOffset > 0.0f) {
            this.textPaint.setColor(Argb.withAlpha(0xFF000000, Math.round(78.0f * (Argb.alpha(color) / 255.0f))));
            ctx.drawString(text, x, drawY + shadowOffset, font, this.textPaint);
        }

        this.textPaint.setColor(color);
        ctx.drawString(text, x, drawY, font, this.textPaint);
    }

    private float measureHudWidth() {
        float maxWidth = this.isModernStyle() ? this.effectiveMinWidth() : OLD_MIN_WIDTH;
        for (EffectEntry entry : this.effectEntryList) {
            maxWidth = Math.max(maxWidth, this.measureEntryWidth(entry));
        }
        return maxWidth;
    }

    private float measureEntryWidth(EffectEntry entry) {
        return this.isModernStyle() ? this.measureRowWidth(entry) : this.measureOldRowWidth(entry);
    }

    private float measureRowWidth(EffectEntry entry) {
        float rowHeightValue = this.number(this.rowHeight, DEFAULT_ROW_HEIGHT);
        float ringSizeValue = Math.min(this.number(this.ringSize, DEFAULT_RING_SIZE), Math.max(12.0f, rowHeightValue - 4.0f));
        float padding = this.number(this.paddingX, DEFAULT_PADDING_X);
        float gap = this.number(this.textGap, DEFAULT_TEXT_GAP);
        float nameWidth = GlHelper.getStringWidth(entry.titleText, this.effectNameFont);
        float durationWidth = GlHelper.getStringWidth(entry.durationText, this.durationFont);
        return Math.max(this.effectiveMinWidth(),
                padding * 2.0f + ringSizeValue + gap + nameWidth + durationWidth + 8.0f);
    }

    private float measureOldRowWidth(EffectEntry entry) {
        float nameWidth = GlHelper.getStringWidth(entry.effectName, this.oldEffectNameFont);
        float durationWidth = GlHelper.getStringWidth(entry.durationText, this.oldDurationFont);
        return Math.max(OLD_MIN_WIDTH, OLD_ICON_BOX_WIDTH + nameWidth + durationWidth + OLD_PADDING * 3.0f);
    }

    private float effectiveMinWidth() {
        return Mth.clamp(this.number(this.minWidth, DEFAULT_MIN_WIDTH), 82.0f, MAX_EFFECTIVE_MIN_WIDTH);
    }

    private float durationProgress(EffectEntry entry) {
        MobEffectInstance instance = entry.effectInstance;
        if (instance.isInfiniteDuration() || entry.originalDuration <= 0L) {
            return 1.0f;
        }
        return Mth.clamp((float) instance.getDuration() / (float) entry.originalDuration, 0.0f, 1.0f);
    }

    private int getEffectColor(MobEffect effect) {
        return Argb.interpolate(this.getRawEffectColor(effect), 0xFFFFFFFF, 0.10f);
    }

    private int getRawEffectColor(MobEffect effect) {
        int color = effect.getColor();
        if (color == 0) {
            color = 0x3380FF;
        }
        return 0xFF000000 | color;
    }

    private EffectIcon iconFor(MobEffect effect) {
        if (effect == MobEffects.MOVEMENT_SPEED) return EffectIcon.MOVEMENT_SPEED;
        if (effect == MobEffects.MOVEMENT_SLOWDOWN) return EffectIcon.MOVEMENT_SLOWDOWN;
        if (effect == MobEffects.DIG_SPEED) return EffectIcon.DIG_SPEED;
        if (effect == MobEffects.DIG_SLOWDOWN) return EffectIcon.DIG_SLOWDOWN;
        if (effect == MobEffects.DAMAGE_BOOST) return EffectIcon.DAMAGE_BOOST;
        if (effect == MobEffects.HEAL) return EffectIcon.HEAL;
        if (effect == MobEffects.HARM) return EffectIcon.HARM;
        if (effect == MobEffects.JUMP) return EffectIcon.JUMP;
        if (effect == MobEffects.CONFUSION) return EffectIcon.CONFUSION;
        if (effect == MobEffects.REGENERATION) return EffectIcon.REGENERATION;
        if (effect == MobEffects.DAMAGE_RESISTANCE) return EffectIcon.DAMAGE_RESISTANCE;
        if (effect == MobEffects.FIRE_RESISTANCE) return EffectIcon.FIRE_RESISTANCE;
        if (effect == MobEffects.WATER_BREATHING) return EffectIcon.WATER_BREATHING;
        if (effect == MobEffects.INVISIBILITY) return EffectIcon.INVISIBILITY;
        if (effect == MobEffects.BLINDNESS) return EffectIcon.BLINDNESS;
        if (effect == MobEffects.NIGHT_VISION) return EffectIcon.NIGHT_VISION;
        if (effect == MobEffects.HUNGER) return EffectIcon.HUNGER;
        if (effect == MobEffects.WEAKNESS) return EffectIcon.WEAKNESS;
        if (effect == MobEffects.POISON) return EffectIcon.POISON;
        if (effect == MobEffects.WITHER) return EffectIcon.WITHER;
        if (effect == MobEffects.HEALTH_BOOST) return EffectIcon.HEALTH_BOOST;
        if (effect == MobEffects.ABSORPTION) return EffectIcon.ABSORPTION;
        if (effect == MobEffects.SATURATION) return EffectIcon.SATURATION;
        if (effect == MobEffects.GLOWING) return EffectIcon.GLOWING;
        if (effect == MobEffects.LEVITATION) return EffectIcon.LEVITATION;
        if (effect == MobEffects.LUCK) return EffectIcon.LUCK;
        if (effect == MobEffects.UNLUCK) return EffectIcon.UNLUCK;
        if (effect == MobEffects.SLOW_FALLING) return EffectIcon.SLOW_FALLING;
        if (effect == MobEffects.CONDUIT_POWER) return EffectIcon.CONDUIT_POWER;
        if (effect == MobEffects.DOLPHINS_GRACE) return EffectIcon.DOLPHINS_GRACE;
        if (effect == MobEffects.BAD_OMEN) return EffectIcon.BAD_OMEN;
        if (effect == MobEffects.HERO_OF_THE_VILLAGE) return EffectIcon.HERO_OF_THE_VILLAGE;
        if (effect == MobEffects.DARKNESS) return EffectIcon.DARKNESS;
        return effect.isBeneficial() ? EffectIcon.DEFAULT : EffectIcon.POISON;
    }

    String formatAmplifier(int amplifier) {
        return switch (amplifier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(amplifier);
        };
    }

    String formatDuration(MobEffectInstance effectInstance) {
        if (effectInstance.isInfiniteDuration() || effectInstance.getDuration() > 72000) {
            return "∞";
        }
        return MobEffectUtil.formatDuration(effectInstance, 1.0f).getString();
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

    private boolean isModernStyle() {
        return this.style == null || !"Old".equalsIgnoreCase(this.style.getValue());
    }

    public static final class EffectEntry {
        private final PotionEffectsHud outer;
        private MobEffectInstance effectInstance;
        private long originalDuration;
        private String effectName;
        private String durationText;
        private String titleText;
        private final SmoothAnimationTimer yAnim = new SmoothAnimationTimer();
        private final SmoothAnimationTimer heightAnim = new SmoothAnimationTimer();
        private final SmoothAnimationTimer alphaAnim = new SmoothAnimationTimer();
        private boolean removing;
        private boolean positioned;

        public EffectEntry(PotionEffectsHud outer, MobEffectInstance instance) {
            this.outer = outer;
            this.effectInstance = instance;
            this.originalDuration = Math.max(1L, instance.getDuration());
            this.refreshDisplayText();
            this.heightAnim.setCurrentValue(0.0);
            this.heightAnim.setToValue(0.0);
            this.alphaAnim.setCurrentValue(0.0);
            this.alphaAnim.setToValue(0.0);
        }

        public MobEffect getEffect() {
            return this.effectInstance.getEffect();
        }

        public void updateEffect(MobEffectInstance instance) {
            if (instance.getDuration() > this.effectInstance.getDuration()) {
                this.originalDuration = Math.max(1L, instance.getDuration());
            }
            this.effectInstance = instance;
            this.refreshDisplayText();
        }

        public void show(float targetHeight) {
            if (this.removing) {
                return;
            }
            this.heightAnim.animate(targetHeight, 0.26, Easings.EASE_OUT_POW3);
            this.alphaAnim.animate(1.0, 0.22, Easings.EASE_OUT_POW3);
        }

        public void startRemove() {
            if (this.removing) {
                return;
            }
            this.removing = true;
            this.heightAnim.animate(0.0, 0.18, Easings.EASE_IN_POW3);
            this.alphaAnim.animate(0.0, 0.16, Easings.EASE_IN_POW3);
        }

        public boolean isRemoveDone() {
            return this.removing && this.heightAnim.isDone() && this.alphaAnim.isDone();
        }

        public void tick(float targetHeight) {
            if (!this.removing && this.heightAnim.getToValue() != targetHeight) {
                this.heightAnim.animate(targetHeight, 0.20, Easings.EASE_OUT_POW3);
            }
            this.yAnim.tick();
            this.heightAnim.tick();
            this.alphaAnim.tick();
            if (!this.effectInstance.getEffect().isInstantenous()) {
                this.refreshDisplayText();
            }
        }

        private void refreshDisplayText() {
            this.effectName = this.effectInstance.getEffect().getDisplayName().getString();
            this.durationText = this.outer.formatDuration(this.effectInstance);
            int level = this.effectInstance.getAmplifier() + 1;
            this.titleText = level > 1
                    ? this.effectName + " " + this.outer.formatAmplifier(level)
                    : this.effectName;
        }
    }

    private enum EffectIcon {
        DEFAULT("standing-potion.svg"),
        MOVEMENT_SPEED("speedometer.svg"),
        MOVEMENT_SLOWDOWN("snail-eyes.svg"),
        DIG_SPEED("3d-hammer.svg"),
        DIG_SLOWDOWN("broken-axe.svg"),
        DAMAGE_BOOST("muscle-up.svg"),
        HEAL("healing.svg"),
        HARM("broken-heart.svg"),
        JUMP("jump-across.svg"),
        CONFUSION("maze.svg"),
        REGENERATION("health-potion.svg"),
        DAMAGE_RESISTANCE("shieldcomb.svg"),
        FIRE_RESISTANCE("fire-bottle.svg"),
        WATER_BREATHING("water-bottle.svg"),
        INVISIBILITY("invisible.svg"),
        BLINDNESS("blindfold.svg"),
        NIGHT_VISION("night-vision.svg"),
        HUNGER("stomach.svg"),
        WEAKNESS("arm-sling.svg"),
        POISON("poison-bottle.svg"),
        WITHER("skull-staff.svg"),
        HEALTH_BOOST("heart-plus.svg"),
        ABSORPTION("heart-shield.svg"),
        SATURATION("meal.svg"),
        GLOWING("glowing-artifact.svg"),
        LEVITATION("floating-crystal.svg"),
        LUCK("clover.svg"),
        UNLUCK("broken-heart-zone.svg"),
        SLOW_FALLING("feather.svg"),
        CONDUIT_POWER("magic-trident.svg"),
        DOLPHINS_GRACE("dolphin.svg"),
        BAD_OMEN("raven.svg"),
        HERO_OF_THE_VILLAGE("village.svg"),
        DARKNESS("moon-bats.svg");

        private final String fileName;

        EffectIcon(String fileName) {
            this.fileName = fileName;
        }
    }

    private static final class EffectIconLibrary {
        private static final String BASE_PATH = "/assets/mizulune/textures/hud/effects/icons/";
        private final EnumMap<EffectIcon, SvgIcon> cache = new EnumMap<>(EffectIcon.class);

        boolean draw(DrawContext ctx, EffectIcon icon, float x, float y, float size, Paint paint) {
            SvgIcon svgIcon = this.cache.computeIfAbsent(icon, this::loadIcon);
            if (svgIcon == null || svgIcon.isEmpty()) {
                return false;
            }
            try (Path path = svgIcon.toPath(x, y, size)) {
                ctx.drawPath(path, paint);
            }
            return true;
        }

        private SvgIcon loadIcon(EffectIcon icon) {
            try (InputStream stream = Assets.open(BASE_PATH + icon.fileName)) {
                if (stream == null) {
                    return SvgIcon.empty();
                }
                String svg = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                return SvgIcon.parse(svg);
            } catch (Exception ignored) {
                return SvgIcon.empty();
            }
        }
    }

    private static final class SvgIcon {
        private static final Pattern VIEW_BOX_PATTERN = Pattern.compile("viewBox\\s*=\\s*\"([^\"]+)\"");
        private static final Pattern PATH_PATTERN = Pattern.compile("<path\\b[^>]*\\bd\\s*=\\s*\"([^\"]+)\"[^>]*>");
        private final ViewBox viewBox;
        private final List<SvgOp> ops;

        private SvgIcon(ViewBox viewBox, List<SvgOp> ops) {
            this.viewBox = viewBox;
            this.ops = ops;
        }

        static SvgIcon empty() {
            return new SvgIcon(new ViewBox(0.0f, 0.0f, 512.0f, 512.0f), List.of());
        }

        static SvgIcon parse(String svg) {
            ViewBox viewBox = parseViewBox(svg);
            List<SvgOp> ops = new ArrayList<>();
            Matcher matcher = PATH_PATTERN.matcher(svg);
            SvgPathParser parser = new SvgPathParser();
            while (matcher.find()) {
                String d = matcher.group(1);
                if (!isBackgroundPath(d)) {
                    ops.addAll(parser.parse(d));
                }
            }
            return new SvgIcon(viewBox, ops);
        }

        boolean isEmpty() {
            return this.ops.isEmpty();
        }

        Path toPath(float x, float y, float size) {
            Path path = new Path();
            float scale = Math.min(size / Math.max(1.0f, this.viewBox.width), size / Math.max(1.0f, this.viewBox.height));
            float drawnWidth = this.viewBox.width * scale;
            float drawnHeight = this.viewBox.height * scale;
            float offsetX = x + (size - drawnWidth) * 0.5f - this.viewBox.x * scale;
            float offsetY = y + (size - drawnHeight) * 0.5f - this.viewBox.y * scale;

            for (SvgOp op : this.ops) {
                float[] c = op.coords;
                switch (op.type) {
                    case MOVE_TO -> path.moveTo(offsetX + c[0] * scale, offsetY + c[1] * scale);
                    case LINE_TO -> path.lineTo(offsetX + c[0] * scale, offsetY + c[1] * scale);
                    case QUAD_TO -> path.quadTo(offsetX + c[0] * scale, offsetY + c[1] * scale,
                            offsetX + c[2] * scale, offsetY + c[3] * scale);
                    case CUBIC_TO -> path.cubicTo(offsetX + c[0] * scale, offsetY + c[1] * scale,
                            offsetX + c[2] * scale, offsetY + c[3] * scale,
                            offsetX + c[4] * scale, offsetY + c[5] * scale);
                    case CLOSE -> path.closePath();
                }
            }
            return path;
        }

        private static ViewBox parseViewBox(String svg) {
            Matcher matcher = VIEW_BOX_PATTERN.matcher(svg);
            if (!matcher.find()) {
                return new ViewBox(0.0f, 0.0f, 512.0f, 512.0f);
            }
            String[] parts = matcher.group(1).trim().split("[,\\s]+");
            if (parts.length < 4) {
                return new ViewBox(0.0f, 0.0f, 512.0f, 512.0f);
            }
            try {
                return new ViewBox(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]),
                        Float.parseFloat(parts[2]), Float.parseFloat(parts[3]));
            } catch (NumberFormatException ignored) {
                return new ViewBox(0.0f, 0.0f, 512.0f, 512.0f);
            }
        }

        private static boolean isBackgroundPath(String d) {
            String normalized = d.replaceAll("\\s+", "");
            return "M00h512v512H0z".equals(normalized) || "M00h256v256H0z".equals(normalized);
        }
    }

    private record ViewBox(float x, float y, float width, float height) {
    }

    private enum SvgOpType {
        MOVE_TO,
        LINE_TO,
        QUAD_TO,
        CUBIC_TO,
        CLOSE
    }

    private record SvgOp(SvgOpType type, float... coords) {
    }

    private static final class SvgPathParser {
        private static final Pattern TOKEN_PATTERN = Pattern.compile(
                "[AaCcHhLlMmQqSsTtVvZz]|[-+]?(?:\\d*\\.\\d+|\\d+\\.?\\d*)(?:[eE][-+]?\\d+)?"
        );
        private List<String> tokens = List.of();
        private int index;
        private float currentX;
        private float currentY;
        private float startX;
        private float startY;
        private float lastCubicX;
        private float lastCubicY;
        private float lastQuadX;
        private float lastQuadY;
        private char previousCommand;

        List<SvgOp> parse(String d) {
            this.tokens = this.tokenize(d);
            this.index = 0;
            this.currentX = 0.0f;
            this.currentY = 0.0f;
            this.startX = 0.0f;
            this.startY = 0.0f;
            this.lastCubicX = 0.0f;
            this.lastCubicY = 0.0f;
            this.lastQuadX = 0.0f;
            this.lastQuadY = 0.0f;
            this.previousCommand = 0;

            List<SvgOp> ops = new ArrayList<>();
            char command = 0;
            while (this.index < this.tokens.size()) {
                String token = this.tokens.get(this.index);
                if (isCommand(token)) {
                    command = token.charAt(0);
                    this.index++;
                    if (Character.toUpperCase(command) == 'Z') {
                        this.close(ops);
                        this.previousCommand = command;
                        continue;
                    }
                }
                if (command == 0) {
                    break;
                }
                this.readCommand(command, ops);
            }
            return ops;
        }

        private void readCommand(char command, List<SvgOp> ops) {
            boolean relative = Character.isLowerCase(command);
            switch (Character.toUpperCase(command)) {
                case 'M' -> this.readMove(relative, ops);
                case 'L' -> this.readLine(relative, ops);
                case 'H' -> this.readHorizontal(relative, ops);
                case 'V' -> this.readVertical(relative, ops);
                case 'C' -> this.readCubic(relative, ops);
                case 'S' -> this.readSmoothCubic(relative, ops);
                case 'Q' -> this.readQuad(relative, ops);
                case 'T' -> this.readSmoothQuad(relative, ops);
                case 'A' -> this.readArcAsLine(relative, ops);
                default -> this.index = this.tokens.size();
            }
            this.previousCommand = command;
        }

        private void readMove(boolean relative, List<SvgOp> ops) {
            boolean first = true;
            while (this.canReadNumbers(2)) {
                float x = this.readNumber();
                float y = this.readNumber();
                if (relative) {
                    x += this.currentX;
                    y += this.currentY;
                }
                if (first) {
                    ops.add(new SvgOp(SvgOpType.MOVE_TO, x, y));
                    this.startX = x;
                    this.startY = y;
                    first = false;
                } else {
                    ops.add(new SvgOp(SvgOpType.LINE_TO, x, y));
                }
                this.currentX = x;
                this.currentY = y;
            }
        }

        private void readLine(boolean relative, List<SvgOp> ops) {
            while (this.canReadNumbers(2)) {
                float x = this.readNumber();
                float y = this.readNumber();
                if (relative) {
                    x += this.currentX;
                    y += this.currentY;
                }
                ops.add(new SvgOp(SvgOpType.LINE_TO, x, y));
                this.currentX = x;
                this.currentY = y;
            }
        }

        private void readHorizontal(boolean relative, List<SvgOp> ops) {
            while (this.canReadNumbers(1)) {
                float x = this.readNumber();
                if (relative) {
                    x += this.currentX;
                }
                ops.add(new SvgOp(SvgOpType.LINE_TO, x, this.currentY));
                this.currentX = x;
            }
        }

        private void readVertical(boolean relative, List<SvgOp> ops) {
            while (this.canReadNumbers(1)) {
                float y = this.readNumber();
                if (relative) {
                    y += this.currentY;
                }
                ops.add(new SvgOp(SvgOpType.LINE_TO, this.currentX, y));
                this.currentY = y;
            }
        }

        private void readCubic(boolean relative, List<SvgOp> ops) {
            while (this.canReadNumbers(6)) {
                float x1 = this.readNumber();
                float y1 = this.readNumber();
                float x2 = this.readNumber();
                float y2 = this.readNumber();
                float x = this.readNumber();
                float y = this.readNumber();
                if (relative) {
                    x1 += this.currentX;
                    y1 += this.currentY;
                    x2 += this.currentX;
                    y2 += this.currentY;
                    x += this.currentX;
                    y += this.currentY;
                }
                ops.add(new SvgOp(SvgOpType.CUBIC_TO, x1, y1, x2, y2, x, y));
                this.lastCubicX = x2;
                this.lastCubicY = y2;
                this.currentX = x;
                this.currentY = y;
            }
        }

        private void readSmoothCubic(boolean relative, List<SvgOp> ops) {
            while (this.canReadNumbers(4)) {
                float x1 = this.isPreviousCubic() ? this.currentX * 2.0f - this.lastCubicX : this.currentX;
                float y1 = this.isPreviousCubic() ? this.currentY * 2.0f - this.lastCubicY : this.currentY;
                float x2 = this.readNumber();
                float y2 = this.readNumber();
                float x = this.readNumber();
                float y = this.readNumber();
                if (relative) {
                    x2 += this.currentX;
                    y2 += this.currentY;
                    x += this.currentX;
                    y += this.currentY;
                }
                ops.add(new SvgOp(SvgOpType.CUBIC_TO, x1, y1, x2, y2, x, y));
                this.lastCubicX = x2;
                this.lastCubicY = y2;
                this.currentX = x;
                this.currentY = y;
            }
        }

        private void readQuad(boolean relative, List<SvgOp> ops) {
            while (this.canReadNumbers(4)) {
                float x1 = this.readNumber();
                float y1 = this.readNumber();
                float x = this.readNumber();
                float y = this.readNumber();
                if (relative) {
                    x1 += this.currentX;
                    y1 += this.currentY;
                    x += this.currentX;
                    y += this.currentY;
                }
                ops.add(new SvgOp(SvgOpType.QUAD_TO, x1, y1, x, y));
                this.lastQuadX = x1;
                this.lastQuadY = y1;
                this.currentX = x;
                this.currentY = y;
            }
        }

        private void readSmoothQuad(boolean relative, List<SvgOp> ops) {
            while (this.canReadNumbers(2)) {
                float x1 = this.isPreviousQuad() ? this.currentX * 2.0f - this.lastQuadX : this.currentX;
                float y1 = this.isPreviousQuad() ? this.currentY * 2.0f - this.lastQuadY : this.currentY;
                float x = this.readNumber();
                float y = this.readNumber();
                if (relative) {
                    x += this.currentX;
                    y += this.currentY;
                }
                ops.add(new SvgOp(SvgOpType.QUAD_TO, x1, y1, x, y));
                this.lastQuadX = x1;
                this.lastQuadY = y1;
                this.currentX = x;
                this.currentY = y;
            }
        }

        private void readArcAsLine(boolean relative, List<SvgOp> ops) {
            while (this.canReadNumbers(7)) {
                this.readNumber();
                this.readNumber();
                this.readNumber();
                this.readNumber();
                this.readNumber();
                float x = this.readNumber();
                float y = this.readNumber();
                if (relative) {
                    x += this.currentX;
                    y += this.currentY;
                }
                ops.add(new SvgOp(SvgOpType.LINE_TO, x, y));
                this.currentX = x;
                this.currentY = y;
            }
        }

        private void close(List<SvgOp> ops) {
            ops.add(new SvgOp(SvgOpType.CLOSE));
            this.currentX = this.startX;
            this.currentY = this.startY;
        }

        private boolean isPreviousCubic() {
            char previous = Character.toUpperCase(this.previousCommand);
            return previous == 'C' || previous == 'S';
        }

        private boolean isPreviousQuad() {
            char previous = Character.toUpperCase(this.previousCommand);
            return previous == 'Q' || previous == 'T';
        }

        private List<String> tokenize(String d) {
            List<String> result = new ArrayList<>();
            Matcher matcher = TOKEN_PATTERN.matcher(d);
            while (matcher.find()) {
                result.add(matcher.group());
            }
            return result;
        }

        private boolean canReadNumbers(int count) {
            if (this.index + count > this.tokens.size()) {
                return false;
            }
            for (int i = 0; i < count; i++) {
                if (isCommand(this.tokens.get(this.index + i))) {
                    return false;
                }
            }
            return true;
        }

        private float readNumber() {
            return Float.parseFloat(this.tokens.get(this.index++));
        }

        private static boolean isCommand(String token) {
            return token != null && token.length() == 1 && Character.isLetter(token.charAt(0));
        }
    }
}
