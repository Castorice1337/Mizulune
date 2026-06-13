package shit.zen.hud.target;

import java.util.Locale;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.modules.impl.render.NameProtect;
import shit.zen.render.DrawContext;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
import shit.zen.render.LiquidGlassSettings;
import shit.zen.render.LiquidGlassStyle;
import shit.zen.render.Paint;
import shit.zen.render.Renderer;
import shit.zen.render.RoundedRectangle;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.utils.render.RenderUtil;

public class LiteTargetStyle extends TargetStyle {
    public static final float HEIGHT = 45.0f;
    private static final float MIN_ASPECT = 3.1f;
    private static final float CORNER_RADIUS = 4.0f;
    private static final float PADDING = 5.0f;
    private static final float AVATAR_SIZE = 28.0f;
    private static final float CONTENT_GAP = 8.0f;
    private static final float MIN_BAR_WIDTH = 58.0f;
    private static final float BAR_HEIGHT = 2.0f;
    private static final float BAR_BOTTOM_GAP = 3.0f;
    private static final long HIDE_GRACE_MS = 300L;

    private final FontRenderer nameFont = FontPresets.pingfang(17.0f);
    private final FontRenderer hpFont = FontPresets.pingfang(14.0f);
    private final SmoothAnimationTimer fadeAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer slideAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer contentAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer headScaleAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer hurtFlashAnim = new SmoothAnimationTimer();
    private LivingEntity currentTarget;
    private boolean visible;
    private long lastActiveTime;
    private int lastHurtTime;
    private float lastWidth = HEIGHT * MIN_ASPECT;

    public LiteTargetStyle() {
        super("Lite");
        this.slideAnim.setCurrentValue(4.0);
        this.slideAnim.setToValue(4.0);
        this.headScaleAnim.setCurrentValue(1.0);
        this.headScaleAnim.setToValue(1.0);
    }

    @Override
    public TargetStyle.Size render(Render2DEvent event, LivingEntity livingEntity, SmoothAnimationTimer healthAnim,
                                   SmoothAnimationTimer healthLagAnim, float healthPct, float x, float y,
                                   boolean liquidGlass) {
        boolean hasTarget = livingEntity != null;
        long now = System.currentTimeMillis();
        boolean targetChanged = false;
        if (hasTarget) {
            this.lastActiveTime = now;
            if (this.currentTarget != livingEntity) {
                this.currentTarget = livingEntity;
                this.lastHurtTime = livingEntity.hurtTime;
                targetChanged = true;
            }
        }

        boolean shouldShow = hasTarget || now - this.lastActiveTime < HIDE_GRACE_MS;
        if (shouldShow != this.visible) {
            this.visible = shouldShow;
            if (this.visible) {
                this.startIntro();
            } else {
                this.fadeAnim.animate(0.0, 0.14, Easings.EASE_IN_POW3);
                this.slideAnim.animate(4.0, 0.14, Easings.EASE_IN_POW3);
                this.contentAnim.animate(0.0, 0.14, Easings.EASE_IN_POW3);
            }
        } else if (targetChanged && this.visible) {
            this.startIntro();
        }

        this.fadeAnim.tick();
        this.slideAnim.tick();
        this.contentAnim.tick();
        this.headScaleAnim.tick();
        this.hurtFlashAnim.tick();

        LivingEntity target = this.currentTarget;
        float finalWidth = this.measureWidth(target);
        this.lastWidth = finalWidth;
        float fade = this.fadeAnim.getValueF();
        if (fade <= 0.01f || target == null) {
            if (!this.visible) {
                this.currentTarget = null;
            }
            return new TargetStyle.Size(this.lastWidth, HEIGHT);
        }

        if (hasTarget && livingEntity.hurtTime > this.lastHurtTime) {
            this.headScaleAnim.setCurrentValue(0.82);
            this.headScaleAnim.animate(1.0, 0.7, Easings.EASE_OUT_ELASTIC);
            this.hurtFlashAnim.setCurrentValue(1.0);
            this.hurtFlashAnim.animate(0.0, 0.42, Easings.EASE_OUT_POW3);
        }
        if (hasTarget) {
            this.lastHurtTime = livingEntity.hurtTime;
        }

        float drawY = y + this.slideAnim.getValueF();
        if (!liquidGlass) {
            RenderUtil.drawBlurredRect(event.guiGraphics().pose(), x, drawY, finalWidth, HEIGHT,
                    CORNER_RADIUS, 12.0f, 0.82f * fade, 0);
        }

        Renderer.render(event.guiGraphics(), drawContext -> {
            RoundedRectangle bounds = RoundedRectangle.ofXYWHR(x, drawY, finalWidth, HEIGHT, CORNER_RADIUS);
            this.drawBackground(drawContext, bounds, fade, liquidGlass);
            drawContext.save();
            drawContext.clipRoundedRect(bounds, true);
            this.drawContent(drawContext, target, healthAnim, healthLagAnim, x, drawY, finalWidth,
                    fade * this.contentAnim.getValueF());
            drawContext.restore();
        });

        return new TargetStyle.Size(finalWidth, HEIGHT);
    }

    private void startIntro() {
        this.fadeAnim.animate(1.0, 0.22, Easings.EASE_OUT_POW3);
        this.slideAnim.setCurrentValue(4.0);
        this.slideAnim.animate(0.0, 0.24, Easings.EASE_OUT_POW3);
        this.contentAnim.setCurrentValue(0.0);
        this.contentAnim.animate(1.0, 0.26, Easings.EASE_OUT_POW3);
        this.headScaleAnim.setCurrentValue(1.0);
        this.headScaleAnim.setToValue(1.0);
        this.hurtFlashAnim.setCurrentValue(0.0);
        this.hurtFlashAnim.setToValue(0.0);
    }

    private float measureWidth(LivingEntity target) {
        if (target == null) {
            return this.lastWidth;
        }
        String name = this.displayName(target);
        String hp = this.healthText(target);
        float contentWidth = Math.max(MIN_BAR_WIDTH, Math.max(
                GlHelper.getStringWidth(name, this.nameFont),
                GlHelper.getStringWidth(hp, this.hpFont)));
        float naturalWidth = PADDING + AVATAR_SIZE + CONTENT_GAP + contentWidth + PADDING;
        return Math.max(HEIGHT * MIN_ASPECT, naturalWidth);
    }

    private void drawBackground(DrawContext drawContext, RoundedRectangle bounds, float alpha, boolean liquidGlass) {
        drawContext.drawBlurredRoundedRect(bounds, 0.0f, 1.0f, liquidGlass ? 18.0f : 7.0f, liquidGlass ? 1.0f : 0.4f,
                this.withAlpha(liquidGlass ? 0x30000000 : 0x42000000, alpha));
        try (Paint paint = new Paint()) {
            if (liquidGlass) {
                drawContext.drawLiquidGlassPanel(bounds, this.glassStyle(alpha));
            } else {
                paint.setColor(this.withAlpha(0x8A24262B, alpha));
                drawContext.drawRoundedRect(bounds, paint);
            }
            paint.setColor(this.withAlpha(liquidGlass ? 0x55FFFFFF : 0x66000000, alpha));
            paint.setStrokeCap(Paint.StrokeCap.STROKE);
            paint.setStrokeWidth(0.8f);
            drawContext.save();
            drawContext.clipRoundedRect(bounds, true);
            drawContext.drawRoundedRect(bounds, paint);
            drawContext.restore();
        }
    }

    private LiquidGlassStyle glassStyle(float alpha) {
        return LiquidGlassStyle.builder()
                .power(CORNER_RADIUS)
                .refractionPower(LiquidGlassSettings.getRefractionPower())
                .refractionStrength(LiquidGlassSettings.getRefractionStrength())
                .noise(LiquidGlassSettings.getNoise())
                .glow(0.16f, 0.03f)
                .glowEdges(0.0f, 0.85f)
                .blurIterations(LiquidGlassSettings.getBlurIterations())
                .blurRadius(LiquidGlassSettings.getBlurRadius())
                .blurDownscale(LiquidGlassSettings.getBlurDownscale())
                .opacity(LiquidGlassSettings.getOpacity() * Mth.clamp(alpha, 0.0f, 1.0f))
                .tint(this.withAlpha(0x66D9F2FF, alpha), 0.22f)
                .chromaStrength(0.012f)
                .darkness(Math.max(0.06f, LiquidGlassSettings.getDarkness()) * Mth.clamp(alpha, 0.0f, 1.0f))
                .build();
    }

    private void drawContent(DrawContext drawContext, LivingEntity target, SmoothAnimationTimer healthAnim,
                             SmoothAnimationTimer healthLagAnim, float x, float y, float width, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        float headScale = Math.max(0.82f, this.headScaleAnim.getValueF());
        float headSize = AVATAR_SIZE * headScale;
        float headX = x + PADDING + (AVATAR_SIZE - headSize) / 2.0f;
        float headY = y + (HEIGHT - AVATAR_SIZE) / 2.0f + (AVATAR_SIZE - headSize) / 2.0f - 2.0f;
        if (target instanceof AbstractClientPlayer player) {
            GlHelper.drawPlayerHead(player, headX, headY, headSize, headSize, alpha);
        } else {
            try (Paint paint = new Paint()) {
                paint.setColor(this.withAlpha(0xFF101114, alpha));
                drawContext.drawRectXYWH(headX, headY, headSize, headSize, paint);
            }
        }
        float flash = this.hurtFlashAnim.getValueF();
        if (flash > 0.01f) {
            try (Paint paint = new Paint()) {
                paint.setColor(this.withAlpha(0xFFFF3030, alpha * flash * 0.62f));
                drawContext.drawRectXYWH(headX, headY, headSize, headSize, paint);
            }
        }

        float textX = x + PADDING + AVATAR_SIZE + CONTENT_GAP;
        float textWidth = width - (PADDING + AVATAR_SIZE + CONTENT_GAP) - PADDING;
        String name = this.displayName(target);
        String hpText = this.healthText(target);
        GlHelper.drawTextShadowLegacy(name, textX, y + 6.0f, this.nameFont, this.withAlpha(0xFFFFFFFF, alpha));
        GlHelper.drawTextShadowLegacy(hpText, textX, y + 24.0f, this.hpFont, this.withAlpha(0xE8F0F0F0, alpha));

        float barX = x + PADDING;
        float barY = y + HEIGHT - BAR_BOTTOM_GAP - BAR_HEIGHT;
        float barWidth = Math.max(1.0f, width - PADDING * 2.0f);
        float contentFactor = this.contentAnim.getValueF();
        try (Paint paint = new Paint()) {
            float lagWidth = Mth.clamp(healthLagAnim.getValueF(), 0.0f, 1.0f) * barWidth * contentFactor;
            paint.setColor(this.withAlpha(0x52FFFFFF, alpha));
            drawContext.drawRectXYWH(barX, barY, lagWidth, BAR_HEIGHT, paint);
            float fillWidth = Mth.clamp(healthAnim.getValueF(), 0.0f, 1.0f) * barWidth * contentFactor;
            paint.setColor(this.withAlpha(0xFFFFFFFF, alpha));
            drawContext.drawRectXYWH(barX, barY, fillWidth, BAR_HEIGHT, paint);
        }
    }

    private String displayName(LivingEntity target) {
        if (target == mc.player) {
            return NameProtect.getProtectedName();
        }
        return NameProtect.replacePlayerName(target.getName().getString());
    }

    private String healthText(LivingEntity target) {
        float current = target.getMaxHealth() > 0.0f ? Math.min(target.getHealth(), 20.0f) : 0.0f;
        return "HP: " + this.formatHealth(current);
    }

    private String formatHealth(float value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private int withAlpha(int color, float alpha) {
        float clamped = Mth.clamp(alpha, 0.0f, 1.0f);
        int baseAlpha = color >>> 24;
        int nextAlpha = Math.round(baseAlpha * clamped);
        return nextAlpha << 24 | color & 0x00FFFFFF;
    }
}
