package shit.zen.hud.target;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.hud.TargetHud;
import shit.zen.modules.impl.render.NameProtect;
import shit.zen.render.DrawContext;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
import shit.zen.render.LiquidGlassStyle;
import shit.zen.render.Paint;
import shit.zen.render.Renderer;
import shit.zen.render.RoundedRectangle;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.value.MizuColor;

public class ModernTargetStyle extends TargetStyle {
    public static final float HEIGHT = 45.0f;
    private static final float MIN_WIDTH = 144.0f;
    private static final float RADIUS = 6.0f;
    private static final float PADDING = 5.0f;
    private static final float AVATAR_SIZE = 32.0f;
    private static final float AVATAR_RADIUS = 5.0f;
    private static final float CONTENT_GAP = 5.5f;
    private static final float BAR_HEIGHT = 5.0f;
    private static final float BAR_RADIUS = 2.5f;
    private static final float ITEM_SIZE = 9.0f;
    private static final float ITEM_GAP = 1.5f;
    private static final long HIDE_GRACE_MS = 320L;
    private static final int PANEL_COLOR = 0xAA2B2F34;

    private final FontRenderer nameFont = FontPresets.pingfang(14.5f);
    private final FontRenderer metaFont = FontPresets.pingfang(12.0f);
    private final SmoothAnimationTimer fadeAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer slideAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer contentAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer headScaleAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer hurtFlashAnim = new SmoothAnimationTimer();
    private LivingEntity currentTarget;
    private boolean visible;
    private long lastActiveTime;
    private float lastTargetHealth = -1.0f;
    private float lastWidth = MIN_WIDTH;

    public ModernTargetStyle() {
        super("Modern");
        this.slideAnim.setCurrentValue(5.0);
        this.slideAnim.setToValue(5.0);
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
                this.lastTargetHealth = livingEntity.getHealth();
                targetChanged = true;
            }
        }

        boolean shouldShow = hasTarget || now - this.lastActiveTime < HIDE_GRACE_MS;
        if (shouldShow != this.visible) {
            this.visible = shouldShow;
            if (this.visible) {
                this.startIntro();
            } else {
                this.fadeAnim.animate(0.0, 0.16, Easings.EASE_IN_POW3);
                this.slideAnim.animate(5.0, 0.16, Easings.EASE_IN_POW3);
                this.contentAnim.animate(0.0, 0.16, Easings.EASE_IN_POW3);
            }
        } else if (targetChanged && this.visible) {
            this.startIntro();
        }

        if (hasTarget) {
            this.updateHurtAnimation(livingEntity);
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
                this.lastTargetHealth = -1.0f;
            }
            return new TargetStyle.Size(this.lastWidth, HEIGHT);
        }

        float drawY = y + this.slideAnim.getValueF();
        List<ItemRender> itemRenders = new ArrayList<>();
        Renderer.render(event.guiGraphics(), drawContext -> {
            RoundedRectangle bounds = RoundedRectangle.ofXYWHR(x, drawY, finalWidth, HEIGHT, RADIUS);
            this.drawBackground(drawContext, bounds, fade);
            drawContext.save();
            drawContext.clipRoundedRect(bounds, true);
            this.drawContent(drawContext, itemRenders, target, healthAnim, healthLagAnim, x, drawY, finalWidth,
                    fade * this.contentAnim.getValueF());
            drawContext.restore();
        });
        this.renderItems(event, itemRenders);

        return new TargetStyle.Size(finalWidth, HEIGHT);
    }

    private void startIntro() {
        this.fadeAnim.animate(1.0, 0.22, Easings.EASE_OUT_POW3);
        this.slideAnim.setCurrentValue(5.0);
        this.slideAnim.animate(0.0, 0.25, Easings.EASE_OUT_POW3);
        this.contentAnim.setCurrentValue(0.0);
        this.contentAnim.animate(1.0, 0.28, Easings.EASE_OUT_POW3);
        this.headScaleAnim.setCurrentValue(1.0);
        this.headScaleAnim.setToValue(1.0);
        this.hurtFlashAnim.setCurrentValue(0.0);
        this.hurtFlashAnim.setToValue(0.0);
    }

    private void updateHurtAnimation(LivingEntity target) {
        float health = target.getHealth();
        if (this.lastTargetHealth >= 0.0f && health < this.lastTargetHealth - 0.05f) {
            this.headScaleAnim.setCurrentValue(0.78);
            this.headScaleAnim.animate(1.0, 0.72, Easings.EASE_OUT_ELASTIC);
            this.hurtFlashAnim.setCurrentValue(1.0);
            this.hurtFlashAnim.animate(0.0, 0.44, Easings.EASE_OUT_POW3);
        }
        this.lastTargetHealth = health;
    }

    private float measureWidth(LivingEntity target) {
        if (target == null) {
            return this.lastWidth;
        }
        float textWidth = Math.max(
                GlHelper.getStringWidth(this.displayName(target), this.nameFont),
                GlHelper.getStringWidth(this.healthText(target) + " |", this.metaFont)
                        + this.itemStripWidth(this.collectItemStacks(target)) + 7.0f);
        return Math.max(MIN_WIDTH, PADDING + AVATAR_SIZE + CONTENT_GAP + textWidth + PADDING);
    }

    private float itemStripWidth(List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return 0.0f;
        }
        return stacks.size() * ITEM_SIZE + (stacks.size() - 1) * ITEM_GAP;
    }

    private void drawBackground(DrawContext drawContext, RoundedRectangle bounds, float alpha) {
        TargetHud config = TargetHud.INSTANCE;
        boolean glow = config == null || config.isModernGlowEnabled();
        if (glow) {
            drawContext.drawBlurredRoundedRect(bounds, 0.0f, 1.0f, 18.0f, 3.0f,
                    this.withAlpha(PANEL_COLOR, alpha * 0.62f));
            drawContext.drawBlurredRoundedRect(bounds, 0.0f, 2.0f, 34.0f, 8.0f,
                    this.withAlpha(PANEL_COLOR, alpha * 0.34f));
            drawContext.drawBlurredRoundedRect(bounds, 0.0f, 3.0f, 48.0f, 13.0f,
                    this.withAlpha(PANEL_COLOR, alpha * 0.16f));
        }
        boolean backgroundBlur = config == null || config.isModernBackgroundBlurEnabled();
        if (backgroundBlur) {
            drawContext.drawLiquidGlassPanel(bounds, this.backgroundBlurStyle(alpha));
        }
        try (Paint paint = new Paint()) {
            paint.setColor(this.withAlpha(PANEL_COLOR, alpha));
            drawContext.drawRoundedRect(bounds, paint);
        }
    }

    private LiquidGlassStyle backgroundBlurStyle(float alpha) {
        return LiquidGlassStyle.builder()
                .power(RADIUS)
                .refractionPower(1.0f)
                .refractionStrength(0.0f)
                .noise(0.0f)
                .glow(0.0f, 0.0f)
                .glowEdges(0.0f, 0.85f)
                .blurIterations(2)
                .blurRadius(10.0f)
                .blurDownscale(1.0f)
                .opacity(0.38f * Mth.clamp(alpha, 0.0f, 1.0f))
                .tint(0x00000000, 0.0f)
                .chromaStrength(0.0f)
                .darkness(0.0f)
                .build();
    }

    private void drawContent(DrawContext drawContext, List<ItemRender> itemRenders, LivingEntity target,
                             SmoothAnimationTimer healthAnim, SmoothAnimationTimer healthLagAnim,
                             float x, float y, float width, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        this.drawAvatar(drawContext, target, x, y, alpha);

        float textX = x + PADDING + AVATAR_SIZE + CONTENT_GAP;
        float nameY = y + 6.0f;
        float metaY = y + 19.0f;
        String name = this.displayName(target);
        GlHelper.drawTextWithShadow(name, textX, nameY, this.nameFont, this.withAlpha(0xFFF5F6FF, alpha));

        String hp = this.healthText(target);
        int metaColor = this.withAlpha(0xEAF4F7FA, alpha);
        GlHelper.drawTextWithShadow(hp, textX, metaY, this.metaFont, metaColor);
        float sepX = textX + GlHelper.getStringWidth(hp, this.metaFont) + 4.0f;
        GlHelper.drawTextWithShadow("|", sepX, metaY, this.metaFont, this.withAlpha(0x9FFFFFFF, alpha));
        float itemX = sepX + GlHelper.getStringWidth("|", this.metaFont) + 5.0f;
        float itemY = y + 18.0f;
        for (ItemStack stack : this.collectItemStacks(target)) {
            itemRenders.add(new ItemRender(stack, itemX, itemY, alpha));
            itemX += ITEM_SIZE + ITEM_GAP;
        }

        float barX = textX;
        float barY = y + 32.5f;
        float barWidth = Math.max(1.0f, width - textX + x - PADDING);
        this.drawHealthBar(drawContext, barX, barY, barWidth, healthAnim, healthLagAnim, alpha);
    }

    private void drawAvatar(DrawContext drawContext, LivingEntity target, float x, float y, float alpha) {
        float headScale = Mth.clamp(this.headScaleAnim.getValueF(), 0.78f, 1.12f);
        float headSize = AVATAR_SIZE * headScale;
        float headX = x + PADDING + (AVATAR_SIZE - headSize) / 2.0f;
        float headY = y + PADDING + (AVATAR_SIZE - headSize) / 2.0f;
        float radius = AVATAR_RADIUS * headScale;
        if (target instanceof AbstractClientPlayer player) {
            GlHelper.drawPlayerHeadRounded(player, headX, headY, headSize, headSize, alpha, radius);
        } else {
            try (Paint paint = new Paint()) {
                paint.setColor(this.withAlpha(0xFF1E2228, alpha));
                drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(headX, headY, headSize, headSize, radius), paint);
            }
        }
        float flash = this.hurtFlashAnim.getValueF();
        if (flash > 0.01f) {
            try (Paint paint = new Paint()) {
                paint.setColor(this.withAlpha(0xFFFF3030, alpha * flash * 0.58f));
                drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(headX, headY, headSize, headSize, radius), paint);
            }
        }
    }

    private void drawHealthBar(DrawContext drawContext, float x, float y, float width,
                               SmoothAnimationTimer healthAnim, SmoothAnimationTimer healthLagAnim, float alpha) {
        TargetHud config = TargetHud.INSTANCE;
        MizuColor start = config != null ? config.modernHealthStart() : MizuColor.ofRgb(0xEA, 0xF7, 0xFF);
        MizuColor end = config != null ? config.modernHealthEnd() : MizuColor.ofRgb(0xFF, 0xC4, 0xF1);
        boolean glow = config == null || config.isModernGlowEnabled();
        float lagPct = Mth.clamp(healthLagAnim.getValueF(), 0.0f, 1.0f);
        float fillPct = Mth.clamp(healthAnim.getValueF(), 0.0f, 1.0f);
        float lagWidth = width * lagPct;
        float fillWidth = width * fillPct;
        try (Paint paint = new Paint()) {
            paint.setColor(this.withAlpha(0x36FFFFFF, alpha));
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, width, BAR_HEIGHT, BAR_RADIUS), paint);
        }
        if (lagWidth > fillWidth + 0.5f) {
            this.drawGradientBar(drawContext, x, y, lagWidth, start, end, alpha * 0.34f);
        }
        if (fillWidth > 0.5f) {
            if (glow) {
                drawContext.drawBlurredRoundedRect(RoundedRectangle.ofXYWHR(x, y, fillWidth, BAR_HEIGHT, BAR_RADIUS),
                        0.0f, 0.0f, 8.0f, 2.0f, this.colorWithAlpha(end, alpha * 0.24f));
            }
            this.drawGradientBar(drawContext, x, y, fillWidth, start, end, alpha);
        }
    }

    private void drawGradientBar(DrawContext drawContext, float x, float y, float width,
                                 MizuColor start, MizuColor end, float alpha) {
        try (Paint paint = new Paint()) {
            paint.setGradCoords(new Paint.GradientCoords(
                    x, y,
                    x + width, y,
                    this.colorWithAlpha(start, alpha),
                    this.colorWithAlpha(end, alpha)));
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, width, BAR_HEIGHT, BAR_RADIUS), paint);
        }
    }

    private void renderItems(Render2DEvent event, List<ItemRender> itemRenders) {
        if (itemRenders.isEmpty()) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (ItemRender item : itemRenders) {
            PoseStack pose = event.guiGraphics().pose();
            pose.pushPose();
            pose.translate(item.x(), item.y(), 0.0f);
            float scale = ITEM_SIZE / 16.0f;
            pose.scale(scale, scale, 1.0f);
            event.guiGraphics().setColor(1.0f, 1.0f, 1.0f, Mth.clamp(item.alpha(), 0.0f, 1.0f));
            event.guiGraphics().renderItem(item.stack(), 0, 0);
            pose.popPose();
        }
        event.guiGraphics().setColor(1.0f, 1.0f, 1.0f, 1.0f);
        event.guiGraphics().flush();
        RenderSystem.disableBlend();
    }

    private List<ItemStack> collectItemStacks(LivingEntity target) {
        List<ItemStack> stacks = new ArrayList<>();
        this.addStack(stacks, target.getItemBySlot(EquipmentSlot.HEAD));
        this.addStack(stacks, target.getItemBySlot(EquipmentSlot.CHEST));
        this.addStack(stacks, target.getItemBySlot(EquipmentSlot.LEGS));
        this.addStack(stacks, target.getItemBySlot(EquipmentSlot.FEET));
        this.addStack(stacks, target.getMainHandItem());
        this.addStack(stacks, target.getOffhandItem());
        return stacks;
    }

    private void addStack(List<ItemStack> stacks, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            stacks.add(stack);
        }
    }

    private String displayName(LivingEntity target) {
        if (target == mc.player) {
            return NameProtect.getProtectedName();
        }
        return NameProtect.replacePlayerName(target.getName().getString());
    }

    private String healthText(LivingEntity target) {
        float health = target.getMaxHealth() > 0.0f ? Math.min(target.getHealth(), 20.0f) : 0.0f;
        float rounded = Math.round(health);
        if (Math.abs(health - rounded) <= 0.05f) {
            return String.format(Locale.US, "%dHP", Math.round(rounded));
        }
        return String.format(Locale.US, "%.1fHP", health);
    }

    private int colorWithAlpha(MizuColor color, float alpha) {
        return color.withAlpha(Math.round((float)color.alpha() * Mth.clamp(alpha, 0.0f, 1.0f))).toArgb();
    }

    private int withAlpha(int color, float alpha) {
        float clamped = Mth.clamp(alpha, 0.0f, 1.0f);
        int baseAlpha = color >>> 24;
        int nextAlpha = Math.round(baseAlpha * clamped);
        return nextAlpha << 24 | color & 0x00FFFFFF;
    }

    private record ItemRender(ItemStack stack, float x, float y, float alpha) {
    }
}
