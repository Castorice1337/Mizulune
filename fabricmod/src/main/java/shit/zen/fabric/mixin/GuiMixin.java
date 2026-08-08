package shit.zen.fabric.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.RenderHookCallbacks;

/** Fabric adapter for scoreboard and first-person texture overlay suppression. */
@Mixin(Gui.class)
abstract class GuiMixin {
    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void mizulune$displayScoreboardSidebar(
            GuiGraphics graphics,
            Objective objective,
            CallbackInfo callbackInfo) {
        if (RenderHookCallbacks.onScoreboardSidebar().handled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void mizulune$renderTextureOverlay(
            GuiGraphics graphics,
            ResourceLocation texture,
            float alpha,
            CallbackInfo callbackInfo) {
        if (RenderHookCallbacks.onTextureOverlay(texture).handled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void mizulune$renderPortalOverlay(
            GuiGraphics graphics,
            float alpha,
            CallbackInfo callbackInfo) {
        if (RenderHookCallbacks.onPortalOverlay().handled()) {
            callbackInfo.cancel();
        }
    }
}
