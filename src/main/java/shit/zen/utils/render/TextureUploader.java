package shit.zen.utils.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;

public final class TextureUploader {
    public static void drawTexture(ResourceLocation resourceLocation, PoseStack poseStack, float x, float y,
                                   float width, float height, float alpha, int color) {
        RenderUtil.drawTexture(resourceLocation, poseStack, x, y, width, height, alpha, color);
    }

    public static void drawTexture(int textureId, PoseStack poseStack, float x, float y, float width, float height,
                                   float alpha, int color) {
        RenderUtil.drawTexture(textureId, poseStack, x, y, width, height, alpha, color);
    }

    private TextureUploader() {
    }
}
