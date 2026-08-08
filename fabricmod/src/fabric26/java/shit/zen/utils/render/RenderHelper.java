package shit.zen.utils.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.awt.image.BufferedImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import shit.zen.fabric.render.FabricRenderBridge;
import shit.zen.fabric.render.LegacyRenderSystem;

/** 26.2 implementation of the shared render utility boundary. */
public final class RenderHelper {
    private RenderHelper() {
    }

    public static void blitRenderTarget(RenderTarget target, PoseStack poseStack, int width, int height) {
        GuiGraphicsExtractor graphics = FabricRenderBridge.currentGui();
        if (graphics == null || target == null || target.getColorTextureView() == null) return;
        graphics.blit(
            target.getColorTextureView(),
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
            0, 0, width, height, 0.0F, 0.0F, 1.0F, 1.0F
        );
    }

    public static void blitRenderTargetSafe(RenderTarget target, PoseStack poseStack, int width, int height) {
        blitRenderTarget(target, poseStack, width, height);
    }

    public static void setTexFilter(int minFilter, int magFilter) {
    }

    public static void pushScaleAround(PoseStack poseStack, float pivotX, float pivotY, float scale) {
        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, 0.0F);
        poseStack.scale(scale, scale, 1.0F);
        poseStack.translate(-pivotX, -pivotY, 0.0F);
    }

    public static void popPose(PoseStack poseStack) {
        poseStack.popPose();
    }

    public static void pushRotateAround(PoseStack poseStack, float pivotX, float pivotY, float angleDegrees) {
        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(angleDegrees));
        poseStack.translate(-pivotX, -pivotY, 0.0F);
    }

    public static void resetShaderColor() {
        LegacyRenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void setShaderColorRGBA(int red, int green, int blue, int alpha) {
        LegacyRenderSystem.setShaderColor(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
    }

    public static void setShaderColorWithAlpha(int color, int alpha) {
        setShaderColorRGBA(Argb.red(color), Argb.green(color), Argb.blue(color), alpha);
    }

    public static void setShaderColor(int color) {
        setShaderColorRGBA(Argb.red(color), Argb.green(color), Argb.blue(color), Argb.alpha(color));
    }

    public static void withBlend(Runnable action) {
        LegacyRenderSystem.enableBlend();
        try { action.run(); } finally { LegacyRenderSystem.disableBlend(); }
    }

    public static void setShaderColorComponents(int color) {
        setShaderColor(color);
    }

    public static DynamicTexture uploadTexture(NativeImage nativeImage, BufferedImage bufferedImage) {
        for (int x = 0; x < bufferedImage.getWidth(); x++) {
            for (int y = 0; y < bufferedImage.getHeight(); y++) {
                nativeImage.setPixel(x, y, bufferedImage.getRGB(x, y));
            }
        }
        return new DynamicTexture(() -> "mizulune/runtime", nativeImage);
    }
}
