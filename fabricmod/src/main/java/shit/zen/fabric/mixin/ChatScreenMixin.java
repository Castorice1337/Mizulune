package shit.zen.fabric.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shit.zen.hook.ChatScreenHookCallbacks;

/** Fabric adapter for editing enabled Mizulune HUD elements while vanilla chat is open. */
@Mixin(ChatScreen.class)
abstract class ChatScreenMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void mizulune$render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callbackInfo) {
        ChatScreenHookCallbacks.onRender(mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void mizulune$mouseClicked(
            double mouseX,
            double mouseY,
            int button,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        ChatScreenHookCallbacks.onMouseClicked(mouseX, mouseY, button);
    }
}
