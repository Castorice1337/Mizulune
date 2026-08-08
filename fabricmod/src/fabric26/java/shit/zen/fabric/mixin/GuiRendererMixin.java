package shit.zen.fabric.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.fabric.render.FabricBlurCompositor;

/** Keeps the world framebuffer available while 26.2 draws the extracted GUI. */
@Mixin(GuiRenderer.class)
abstract class GuiRendererMixin {
    @Inject(method = "draw", at = @At("HEAD"))
    private void mizulune$beginGuiDraw(CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        FabricBlurCompositor.beginGuiDraw(
                minecraft == null ? null : minecraft.gameRenderer.mainRenderTarget());
    }

    @Redirect(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget mizulune$selectGuiTarget(GameRenderer renderer) {
        return FabricBlurCompositor.selectGuiTarget(renderer.mainRenderTarget());
    }

    @Inject(method = "draw", at = @At("RETURN"))
    private void mizulune$finishGuiDraw(CallbackInfo callbackInfo) {
        FabricBlurCompositor.finishGuiDraw();
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void mizulune$closeTargets(CallbackInfo callbackInfo) {
        FabricBlurCompositor.close();
    }
}
