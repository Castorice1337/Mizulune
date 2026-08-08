package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import asm.patchify.annotation.Slice;
import asm.patchify.annotation.WrapInvoke;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.asm.Invocation;
import shit.zen.event.impl.RenderEntityEvent;
import shit.zen.event.impl.RotationAnimationEvent;
import shit.zen.hook.LivingEntityRenderHookCallbacks;

@Patch(LivingEntityRenderer.class)
public class LivingEntityRendererPatch {
    @Inject(
            method = "render",
            desc = "(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRenderPre(
            LivingEntityRenderer<?, ?> renderer, LivingEntity entity, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callbackInfo) {
        if (LivingEntityRenderHookCallbacks.onRenderPre(
                renderer, entity, poseStack, bufferSource, partialTick, packedLight).handled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = "render",
            desc = "(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(At.Type.TAIL)
    )
    public static void onRenderPost(
            LivingEntityRenderer<?, ?> renderer, LivingEntity entity, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callbackInfo) {
        LivingEntityRenderHookCallbacks.onRenderPost(
                renderer, entity, poseStack, bufferSource, partialTick, packedLight);
    }

    // PatchTransformer.wrapInvoke now tries strict owner+name+desc matching first,
    // so this slice index is independent of the other Mth.* wraps in this patch.
    // LivingEntityRenderer.render holds 3 Mth.rotLerp(FFF)F calls in order:
    //   1: body yaw    (Mth.rotLerp(g, yBodyRotO, yBodyRot))
    //   2: head yaw    (Mth.rotLerp(g, yHeadRotO, yHeadRot))      <- we want this
    //   3: head yaw recalc in the conditional branch
    @WrapInvoke(
            method = "render",
            desc = "(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            target = "net/minecraft/util/Mth/rotLerp",
            targetDesc = "(FFF)F",
            slice = @Slice(startIndex = 2, endIndex = 2)
    )
    public static float onRenderHeadYawLerp(
            LivingEntityRenderer<?, ?> renderer, LivingEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            Invocation<Object, Float> original) throws Exception {
        float delta = (Float) original.args().get(0);
        float start = (Float) original.args().get(1);
        float end   = (Float) original.args().get(2);
        return LivingEntityRenderHookCallbacks.headYaw(entity, delta, start, end);
    }

    // Only one Mth.lerp(FFF)F call site in LivingEntityRenderer.render (pitch
    // lerp: Mth.lerp(g, xRotO, getXRot())). The strict matcher in
    // PatchTransformer.wrapInvoke filters out the three Mth.rotLerp calls, so
    // slice (1,1) is the right pick.
    @WrapInvoke(
            method = "render",
            desc = "(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            target = "net/minecraft/util/Mth/lerp",
            targetDesc = "(FFF)F",
            slice = @Slice(startIndex = 1, endIndex = 1)
    )
    public static float onRenderPitchLerp(
            LivingEntityRenderer<?, ?> renderer, LivingEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            Invocation<Object, Float> original) throws Exception {
        float delta = (Float) original.args().get(0);
        float start = (Float) original.args().get(1);
        float end   = (Float) original.args().get(2);
        return LivingEntityRenderHookCallbacks.pitch(entity, delta, start, end);
    }
}
