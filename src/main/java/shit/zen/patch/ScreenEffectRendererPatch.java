package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import shit.zen.modules.impl.render.NoRender;

@Patch(ScreenEffectRenderer.class)
public class ScreenEffectRendererPatch {
    @Inject(
            method = "renderFire",
            desc = "(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRenderFire(Minecraft minecraft, PoseStack poseStack, CallbackInfo callbackInfo) {
        if (NoRender.shouldHideFireOverlay()) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = "renderTex",
            desc = "(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRenderTex(TextureAtlasSprite sprite, PoseStack poseStack, CallbackInfo callbackInfo) {
        if (NoRender.shouldHideBlockOverlay()) {
            callbackInfo.cancel();
        }
    }
}
