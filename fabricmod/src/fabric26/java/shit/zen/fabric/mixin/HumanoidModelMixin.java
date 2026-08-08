package shit.zen.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import shit.zen.hook.LivingEntityRenderHookCallbacks;

/** Fabric 26.2 adapter for the original local-player head yaw/pitch render hooks. */
@Mixin(LivingEntityRenderer.class)
abstract class HumanoidModelMixin {
    @ModifyExpressionValue(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;rotLerp(FFF)F"))
    private float mizulune$headYaw(
            float vanillaHeadYaw,
            LivingEntity entity,
            LivingEntityRenderState state,
            float partialTick) {
        // 1.20.1 wrapped this exact head-yaw interpolation before the renderer
        // derived body-relative yRot. Keep the same ordering in 26.2 so
        // Scaffold/KillAura silent rotations turn the head with the body.
        return LivingEntityRenderHookCallbacks.headYaw(
                entity, partialTick, entity.yHeadRotO, entity.yHeadRot);
    }

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getXRot(F)F"))
    private float mizulune$headPitch(LivingEntity entity, float partialTick) {
        float pitch = entity.getXRot(partialTick);
        return LivingEntityRenderHookCallbacks.pitch(entity, pitch).getPitch();
    }
}
