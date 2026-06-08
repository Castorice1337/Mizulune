package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import shit.zen.ZenClient;
import shit.zen.modules.impl.render.DynamicIsland;

@Patch(ContainerScreen.class)
public class ContainerScreenRenderPatch {
    @Inject(
            method = "render",
            desc = "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRender(ContainerScreen screen, GuiGraphics graphics, int mouseX, int mouseY,
                                float partialTick, CallbackInfo callbackInfo) {
        if (ZenClient.isReady() && DynamicIsland.shouldSuppressChestScreen(screen)) {
            callbackInfo.cancel();
        }
    }
}
