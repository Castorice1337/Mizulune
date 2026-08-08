package shit.zen.fabric.mixin;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shit.zen.hook.HookDecision;
import shit.zen.hook.RenderHookCallbacks;

/** Fabric adapter for NoRender darkness suppression. */
@Mixin(LightTexture.class)
abstract class LightTextureMixin {
    @Inject(method = "getDarknessGamma", at = @At("HEAD"), cancellable = true)
    private void mizulune$getDarknessGamma(
            float partialTicks,
            CallbackInfoReturnable<Float> callbackInfo) {
        setDarknessOverride(callbackInfo);
    }

    @Inject(method = "calculateDarknessScale", at = @At("HEAD"), cancellable = true)
    private void mizulune$calculateDarknessScale(
            LivingEntity entity,
            float darkness,
            float partialTicks,
            CallbackInfoReturnable<Float> callbackInfo) {
        setDarknessOverride(callbackInfo);
    }

    private static void setDarknessOverride(CallbackInfoReturnable<Float> callbackInfo) {
        HookDecision<Float> decision = RenderHookCallbacks.onDarknessScale();
        if (decision.handled()) {
            callbackInfo.setReturnValue(decision.value());
        }
    }
}
