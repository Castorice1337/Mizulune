package shit.zen.fabric.mixin;

import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shit.zen.modules.impl.render.NoRender;

/** Fabric 26.2 adapter for the fog-environment pipeline introduced after 1.20.1. */
@Mixin(MobEffectFogEnvironment.class)
abstract class FogRendererMixin {
    @Shadow
    public abstract Holder<MobEffect> getMobEffect();

    @Inject(method = "isApplicable", at = @At("HEAD"), cancellable = true)
    private void mizulune$filterMobEffectFog(
            FogType fogType,
            Entity entity,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        Holder<MobEffect> effect = this.getMobEffect();
        if ((effect == MobEffects.BLINDNESS && NoRender.shouldHideBlindness())
                || (effect == MobEffects.DARKNESS && NoRender.shouldHideDarkness())) {
            callbackInfo.setReturnValue(false);
        }
    }
}
