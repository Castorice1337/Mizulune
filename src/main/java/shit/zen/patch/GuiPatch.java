package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.Objective;
import shit.zen.hud.ScoreboardHud;
import shit.zen.modules.impl.render.NoRender;

@Patch(Gui.class)
public class GuiPatch {
    @Inject(
            method = "displayScoreboardSidebar",
            desc = "(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(At.Type.HEAD)
    )
    public static void onDisplayScoreboardSidebar(Gui gui, GuiGraphics graphics, Objective objective, CallbackInfo callbackInfo) {
        if (ScoreboardHud.shouldCancelVanillaSidebar()) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = "renderTextureOverlay",
            desc = "(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;F)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRenderTextureOverlay(Gui gui, GuiGraphics graphics, ResourceLocation texture, float alpha, CallbackInfo callbackInfo) {
        if (NoRender.shouldCancelTextureOverlay(texture)) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = "renderPortalOverlay",
            desc = "(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRenderPortalOverlay(Gui gui, GuiGraphics graphics, float alpha, CallbackInfo callbackInfo) {
        if (NoRender.shouldHidePortalOverlay()) {
            callbackInfo.cancel();
        }
    }
}
