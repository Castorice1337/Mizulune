package shit.zen.gui;

import java.awt.Color;
import net.minecraft.resources.ResourceLocation;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.GlRenderEvent;
import shit.zen.render.DrawContext;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
import shit.zen.render.Paint;
import shit.zen.render.Rectangle;
import shit.zen.render.Texture;
import shit.zen.event.EventTarget;
import shit.zen.utils.render.ColorUtil;

public class IntroAnimation
extends ClientBase {
    private static final ResourceLocation LOGO = ResourceLocation.tryParse("mizulune:textures/gui/mizulune_logo.png");
    private static final float TEXT_START_OFFSET_Y = -14.0f;
    private static final float LOGO_SIZE = 44.0f;
    private static final float LOGO_GAP = 10.0f;
    private static volatile boolean isActive = false;
    private long startTime = -1L;
    private boolean finished = false;

    public IntroAnimation() {
        isActive = true;
    }

    public static boolean isRunning() {
        return isActive;
    }

    @EventTarget(value=4)
    public void onRender(GlRenderEvent glRenderEvent) {
        float fadeFactor;
        float bgAlpha;
        long elapsed;
        if (this.finished) {
            return;
        }
        if (this.startTime < 0L) {
            this.startTime = System.currentTimeMillis();
        }
        long dup = elapsed = System.currentTimeMillis() - this.startTime;
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight / 2.0f;
        long zAppearStart = 1100L;
        long fadeOutStart = zAppearStart + 900L + 700L + 500L + 300L + 500L + 1300L;
        if (elapsed <= 800L) {
            float fadeIn = IntroAnimation.easeOutCubic(IntroAnimation.clamp01((float)elapsed / 800.0f));
            bgAlpha = 0.6f * fadeIn;
        } else if (elapsed <= fadeOutStart) {
            bgAlpha = 0.6f;
        } else if (elapsed <= fadeOutStart + 700L) {
            float fadeOut = 1.0f - IntroAnimation.easeInCubic(IntroAnimation.clamp01((float)(elapsed - fadeOutStart) / 700.0f));
            bgAlpha = 0.6f * fadeOut;
        } else {
            this.finish();
            return;
        }
        Paint paint = GlHelper.toPaint(new Color(0, 0, 0, (int)(bgAlpha * 255.0f)));
        GlHelper.drawRect(0.0f, 0.0f, screenWidth, screenHeight, paint);
        float zScale = 1.0f;
        float zAlpha = 0.0f;
        if (elapsed >= zAppearStart) {
            long sinceZ = elapsed - zAppearStart;
            if (sinceZ <= 900L) {
                float zProgress = IntroAnimation.easeOutCubic(IntroAnimation.clamp01((float)sinceZ / 900.0f));
                zScale = IntroAnimation.lerp(2.0f, 1.0f, zProgress);
                zAlpha = zProgress;
            } else {
                zScale = 1.0f;
                zAlpha = 1.0f;
            }
        }
        float slideProgress = 0.0f;
        long slideStart = zAppearStart + 900L + 700L;
        if (elapsed > slideStart && elapsed <= slideStart + 500L) {
            slideProgress = IntroAnimation.easeOutCubic((float)(elapsed - slideStart) / 500.0f);
        } else if (elapsed > slideStart + 500L) {
            slideProgress = 1.0f;
        }
        FontRenderer scaledZFont = FontPresets.axiformaBold(64.0f * zScale);
        FontRenderer baseFont = FontPresets.axiformaBold(64.0f);
        long enStart = slideStart + 500L + 300L;
        float textSettleProgress = 0.0f;
        if (elapsed > enStart && elapsed <= enStart + 500L) {
            textSettleProgress = IntroAnimation.easeOutCubic((float)(elapsed - enStart) / 500.0f);
        } else if (elapsed > enStart + 500L) {
            textSettleProgress = 1.0f;
        }
        float textOffsetY = IntroAnimation.lerp(TEXT_START_OFFSET_Y, 0.0f, textSettleProgress);
        float zWidth = GlHelper.getStringWidth("M", scaledZFont);
        float enWidth = GlHelper.getStringWidth("izulune", baseFont);
        float zCenterX = centerX - zWidth / 2.0f;
        float zSlideX = centerX - (zWidth + 0.0f + enWidth) / 2.0f;
        float zRenderX = IntroAnimation.lerp(zCenterX, zSlideX, slideProgress);
        float zRenderY = centerY - scaledZFont.getMetrics().capHeight() / 2.0f + textOffsetY;
        float enAlpha = 0.0f;
        if (elapsed > enStart && elapsed <= enStart + 500L) {
            enAlpha = fadeFactor = IntroAnimation.easeOutCubic((float)(elapsed - enStart) / 500.0f);
        } else if (elapsed > enStart + 500L) {
            enAlpha = 1.0f;
        }
        fadeFactor = 1.0f;
        if (elapsed > fadeOutStart) {
            fadeFactor = 1.0f - IntroAnimation.clamp01((float)(elapsed - fadeOutStart) / 700.0f);
        }
        int zColor = IntroAnimation.rainbowColor(IntroAnimation.clamp01(zAlpha * fadeFactor), 0);
        GlHelper.drawText("M", zRenderX, zRenderY, scaledZFont, zColor);
        if (enAlpha > 0.0f) {
            float enX = zRenderX + zWidth + 0.0f;
            float enY = centerY - baseFont.getMetrics().capHeight() / 2.0f + textOffsetY;
            int enColor = new Color(1.0f, 1.0f, 1.0f, IntroAnimation.clamp01(enAlpha * fadeFactor)).getRGB();
            GlHelper.drawText("izulune", enX, enY, baseFont, enColor);
        }
        this.drawLogoReveal(glRenderEvent.drawContext(), centerX, centerY, baseFont, elapsed, enStart, fadeFactor, textOffsetY);
    }

    private void drawLogoReveal(DrawContext drawContext, float centerX, float centerY, FontRenderer baseFont,
                                long elapsed, long enStart, float fadeFactor, float textOffsetY) {
        if (drawContext == null || LOGO == null) {
            return;
        }
        long logoStart = enStart + 180L;
        float logoProgress;
        if (elapsed <= logoStart) {
            logoProgress = 0.0f;
        } else if (elapsed <= logoStart + 650L) {
            logoProgress = IntroAnimation.easeOutCubic((float)(elapsed - logoStart) / 650.0f);
        } else {
            logoProgress = 1.0f;
        }
        if (logoProgress <= 0.01f || fadeFactor <= 0.01f) {
            return;
        }
        float textTopY = centerY - baseFont.getMetrics().capHeight() / 2.0f + textOffsetY;
        float revealLineY = textTopY - 6.0f;
        float logoX = centerX - LOGO_SIZE / 2.0f;
        float logoFinalY = revealLineY - LOGO_GAP - LOGO_SIZE;
        float logoY = IntroAnimation.lerp(revealLineY, logoFinalY, logoProgress);
        float clipHeight = Math.max(1.0f, revealLineY - logoFinalY + 2.0f);
        Paint paint = GlHelper.toPaint(IntroAnimation.withAlpha(0xFFFFFFFF, logoProgress * fadeFactor));
        drawContext.save();
        try {
            drawContext.clip(Rectangle.ofXYWH(logoX - 2.0f, logoFinalY - 2.0f, LOGO_SIZE + 4.0f, clipHeight));
            drawContext.drawTexture(new Texture(LOGO, 200, 200),
                    Rectangle.ofXYWH(0.0f, 0.0f, 200.0f, 200.0f),
                    Rectangle.ofXYWH(logoX, logoY, LOGO_SIZE, LOGO_SIZE),
                    paint);
        } finally {
            drawContext.restore();
        }
    }

    private void finish() {
        if (!this.finished) {
            this.finished = true;
            try {
                ZenClient.instance.getEventBus().unregister(this);
            } catch (Throwable throwable) {
                // empty catch block
            }
            isActive = false;
        }
    }

    private static float clamp01(float value) {
        return value < 0.0f ? 0.0f : (value > 1.0f ? 1.0f : value);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float easeOutCubic(float t) {
        float clamped = IntroAnimation.clamp01(t);
        clamped = (float)(1.0 - Math.pow(1.0f - clamped, 3.0));
        return clamped;
    }

    private static float easeInCubic(float t) {
        float clamped = IntroAnimation.clamp01(t);
        clamped = clamped * clamped * clamped;
        return clamped;
    }

    private static int rainbowColor(float alpha, int offset) {
        Color rainbow = ColorUtil.getRainbowColor(12, offset);
        return new Color(rainbow.getRed(), rainbow.getGreen(), rainbow.getBlue(),
                Math.round(255.0f * IntroAnimation.clamp01(alpha))).getRGB();
    }

    private static int withAlpha(int color, float alpha) {
        int nextAlpha = Math.round(255.0f * IntroAnimation.clamp01(alpha));
        return nextAlpha << 24 | color & 0x00FFFFFF;
    }
}
