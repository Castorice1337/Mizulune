package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.LivingEntity;
import shit.zen.modules.impl.render.NoRender;

@Patch(LightTexture.class)
public class LightTexturePatch {
    @Inject(method = "getDarknessGamma", desc = "(F)F", at = @At(At.Type.HEAD))
    public static void onGetDarknessGamma(LightTexture lightTexture, float partialTicks, CallbackInfo callbackInfo) {
        if (NoRender.shouldHideDarkness()) {
            callbackInfo.result = 0.0f;
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = "calculateDarknessScale",
            desc = "(Lnet/minecraft/world/entity/LivingEntity;FF)F",
            at = @At(At.Type.HEAD)
    )
    public static void onCalculateDarknessScale(LightTexture lightTexture, LivingEntity entity, float darkness, float partialTicks,
                                                CallbackInfo callbackInfo) {
        if (NoRender.shouldHideDarkness()) {
            callbackInfo.result = 0.0f;
            callbackInfo.cancel();
        }
    }
}
