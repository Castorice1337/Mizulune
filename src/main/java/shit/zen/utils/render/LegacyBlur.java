package shit.zen.utils.render;

import com.mojang.blaze3d.vertex.PoseStack;

public final class LegacyBlur {
    public static void drawBackdropBlurredRect(PoseStack poseStack, float x, float y, float width, float height,
                                               float radius, float blurRadius, float opacity, int color) {
        RenderUtil.drawBlurredRect(poseStack, x, y, width, height, radius, blurRadius, opacity, color);
    }

    private LegacyBlur() {
    }
}
