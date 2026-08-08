package shit.zen.fabric.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.HookDecision;
import shit.zen.hook.MinecraftHookCallbacks;

/** Fabric adapter for the first loader-neutral Minecraft lifecycle hook set. */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void mizulune$tickHead(CallbackInfo callbackInfo) {
        MinecraftHookCallbacks.onTickHead((Minecraft) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void mizulune$tickTail(CallbackInfo callbackInfo) {
        MinecraftHookCallbacks.onTickTail();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void mizulune$close(CallbackInfo callbackInfo) {
        MinecraftHookCallbacks.onClose();
    }

    @Inject(
        method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
        at = @At("HEAD")
    )
    private void mizulune$clearLevel(Screen screen, CallbackInfo callbackInfo) {
        MinecraftHookCallbacks.onClearLevel(screen);
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void mizulune$setLevel(ClientLevel level, CallbackInfo callbackInfo) {
        MinecraftHookCallbacks.onSetLevel(level);
    }

    @Inject(
        method = "handleKeybinds",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mizulune$handleKeybinds(CallbackInfo callbackInfo) {
        HookDecision<Void> decision = MinecraftHookCallbacks.onHandleKeybinds();
        if (decision.handled()) callbackInfo.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void mizulune$startUseItemHead(CallbackInfo callbackInfo) {
        MinecraftHookCallbacks.onStartUseItemHead((Minecraft) (Object) this);
    }

    // RETURN covers every early return in vanilla startUseItem; TAIL only covers the final one.
    @Inject(method = "startUseItem", at = @At("RETURN"))
    private void mizulune$startUseItemTail(CallbackInfo callbackInfo) {
        MinecraftHookCallbacks.onStartUseItemTail((Minecraft) (Object) this);
    }

    @Redirect(
        method = "shouldEntityAppearGlowing",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;isCurrentlyGlowing()Z"
        )
    )
    private boolean mizulune$shouldEntityGlow(Entity entity) {
        try {
            return MinecraftHookCallbacks.onShouldEntityGlow(entity, entity::isCurrentlyGlowing);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @Inject(method = "resizeDisplay", at = @At("TAIL"))
    private void mizulune$resizeDisplay(CallbackInfo callbackInfo) {
        MinecraftHookCallbacks.onResizeDisplay((Minecraft) (Object) this);
    }
}
