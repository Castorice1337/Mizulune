package shit.zen.fabric.mixin;

import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.KeyboardInputHookCallbacks;

/** Fabric adapter for StrafeEvent and INPUT-source sprint decisions. */
@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mizulune$tick(
            boolean slowDown,
            float sneakMultiplier,
            CallbackInfo callbackInfo) {
        KeyboardInputHookCallbacks.onTick(
                (KeyboardInput) (Object) this,
                slowDown,
                sneakMultiplier);
    }
}
