package shit.zen.fabric.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.RenderHookCallbacks;

/** Fabric adapter for Dynamic Island chest-screen ownership. */
@Mixin(ContainerScreen.class)
abstract class ContainerScreenMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mizulune$render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callbackInfo) {
        if (RenderHookCallbacks.onContainerRender((ContainerScreen) (Object) this).handled()) {
            callbackInfo.cancel();
        }
    }
}
