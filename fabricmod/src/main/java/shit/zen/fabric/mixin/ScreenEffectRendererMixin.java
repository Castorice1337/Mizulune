package shit.zen.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.RenderHookCallbacks;

/** Fabric adapter for fire and in-block first-person overlays. */
@Mixin(ScreenEffectRenderer.class)
abstract class ScreenEffectRendererMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void mizulune$renderFire(
            Minecraft minecraft,
            PoseStack poseStack,
            CallbackInfo callbackInfo) {
        if (RenderHookCallbacks.onFireOverlay().handled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    private static void mizulune$renderTex(
            TextureAtlasSprite sprite,
            PoseStack poseStack,
            CallbackInfo callbackInfo) {
        if (RenderHookCallbacks.onBlockOverlay().handled()) {
            callbackInfo.cancel();
        }
    }
}
