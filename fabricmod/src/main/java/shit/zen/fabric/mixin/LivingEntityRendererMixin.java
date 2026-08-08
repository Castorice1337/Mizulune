package shit.zen.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.LivingEntityRenderHookCallbacks;

/** Fabric adapter for entity render events and silent body/head interpolation. */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @Unique
    private static final ThreadLocal<LivingEntity> MIZULUNE_RENDER_ENTITY = new ThreadLocal<>();

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mizulune$renderHead(
            LivingEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo callbackInfo) {
        if (LivingEntityRenderHookCallbacks.onRenderPre(
                (LivingEntityRenderer<?, ?>) (Object) this,
                entity,
                poseStack,
                bufferSource,
                partialTick,
                packedLight).handled()) {
            callbackInfo.cancel();
            return;
        }
        MIZULUNE_RENDER_ENTITY.set(entity);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;rotLerp(FFF)F",
                    ordinal = 1))
    private float mizulune$headYaw(float delta, float start, float end) {
        return LivingEntityRenderHookCallbacks.headYaw(
                MIZULUNE_RENDER_ENTITY.get(), delta, start, end);
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"))
    private float mizulune$pitch(float delta, float start, float end) {
        return LivingEntityRenderHookCallbacks.pitch(
                MIZULUNE_RENDER_ENTITY.get(), delta, start, end);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mizulune$renderTail(
            LivingEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo callbackInfo) {
        try {
            LivingEntityRenderHookCallbacks.onRenderPost(
                    (LivingEntityRenderer<?, ?>) (Object) this,
                    entity,
                    poseStack,
                    bufferSource,
                    partialTick,
                    packedLight);
        } finally {
            MIZULUNE_RENDER_ENTITY.remove();
        }
    }
}
