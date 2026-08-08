package shit.zen.fabric.mixin;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shit.zen.hook.RenderHookCallbacks;

/** Fabric adapter for blindness/darkness priority fog filtering. */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Inject(method = "getPriorityFogFunction", at = @At("RETURN"), cancellable = true)
    private static void mizulune$getPriorityFogFunction(
            Entity entity,
            float partialTick,
            CallbackInfoReturnable<Object> callbackInfo) {
        callbackInfo.setReturnValue(
                RenderHookCallbacks.filterMobEffectFogFunction(callbackInfo.getReturnValue()));
    }
}
