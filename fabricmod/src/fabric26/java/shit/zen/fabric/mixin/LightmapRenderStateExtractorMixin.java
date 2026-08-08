package shit.zen.fabric.mixin;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.GameRendererHookCallbacks;
import shit.zen.hook.HookDecision;

/** Fabric 26.2 adapter for FullBright after lightmap extraction moved to render state. */
@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapRenderStateExtractorMixin {
    @Inject(method = "extract", at = @At("TAIL"))
    private void mizulune$fullBright(
            LightmapRenderState renderState,
            float partialTicks,
            CallbackInfo callbackInfo) {
        HookDecision<Float> decision = GameRendererHookCallbacks.onFullBrightScale();
        if (decision.handled() && renderState.needsUpdate) {
            renderState.nightVisionEffectIntensity = decision.value();
        }
    }
}
