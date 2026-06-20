package shit.zen.utils.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.AABB;

public final class LegacyGl3D {
    public static void drawSolidBox(AABB box, PoseStack poseStack) {
        RenderUtil.drawSolidBox(box, poseStack);
    }

    public static void drawOutlineBox(AABB box, PoseStack poseStack) {
        RenderUtil.drawOutlineBox(box, poseStack);
    }

    private LegacyGl3D() {
    }
}
