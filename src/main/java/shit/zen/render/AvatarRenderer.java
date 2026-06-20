package shit.zen.render;

import net.minecraft.client.player.AbstractClientPlayer;

public final class AvatarRenderer {
    public static void drawPlayerHeadRounded(AbstractClientPlayer player, float x, float y, float width, float height,
                                             float alpha, float radius) {
        GlHelper.drawPlayerHeadRounded(player, x, y, width, height, alpha, radius);
    }

    private AvatarRenderer() {
    }
}
