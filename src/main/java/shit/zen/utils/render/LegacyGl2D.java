package shit.zen.utils.render;

import com.mojang.blaze3d.vertex.PoseStack;

public final class LegacyGl2D {
    public static void drawRoundedRect(PoseStack poseStack, float x, float y, float width, float height,
                                       float radius, int color) {
        RenderUtil.drawRoundedRect(poseStack, x, y, width, height, radius, color);
    }

    public static void drawFilledRect(PoseStack poseStack, float x, float y, float width, float height, int color) {
        RenderUtil.drawFilledRect(poseStack, x, y, width, height, color);
    }

    private LegacyGl2D() {
    }
}
