package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.scores.Objective;
import shit.zen.hud.ScoreboardHud;

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
}
