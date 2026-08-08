package shit.zen.patch;

import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import shit.zen.hook.ChatScreenHookCallbacks;

@Patch(ChatScreen.class)
public class ChatScreenPatch {
    @Inject(method = "render", desc = "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
    public static void onRender(ChatScreen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo) {
        ChatScreenHookCallbacks.onRender(mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", desc = "(DDI)Z")
    public static void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfo callbackInfo) {
        ChatScreenHookCallbacks.onMouseClicked(mouseX, mouseY, button);
    }
}
