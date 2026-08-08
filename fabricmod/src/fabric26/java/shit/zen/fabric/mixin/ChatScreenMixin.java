package shit.zen.fabric.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shit.zen.hook.ChatScreenHookCallbacks;

/** Fabric 26.2 adapter for HUD dragging while vanilla chat is open. */
@Mixin(ChatScreen.class)
abstract class ChatScreenMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void mizulune$extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callbackInfo) {
        ChatScreenHookCallbacks.onRender(mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void mizulune$mouseClicked(
            MouseButtonEvent event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        ChatScreenHookCallbacks.onMouseClicked(event.x(), event.y(), event.button());
    }
}
