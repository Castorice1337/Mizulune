package shit.zen.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Matrix4f;
import shit.zen.hook.GameRendererHookCallbacks;
import shit.zen.hook.HookDecision;

/** Render adapter stays on vanilla GameRenderer/Gui boundaries for Sodium compatibility. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void mizulune$getNightVisionScale(
        LivingEntity entity,
        float partialTick,
        CallbackInfoReturnable<Float> callbackInfo
    ) {
        callbackInfo.setReturnValue(
            GameRendererHookCallbacks.getNightVisionScale(entity, partialTick)
        );
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            shift = At.Shift.AFTER
        )
    )
    private void mizulune$render(
        float partialTick,
        long nanoTime,
        boolean renderLevel,
        CallbackInfo callbackInfo
    ) {
        GameRendererHookCallbacks.onRender((GameRenderer) (Object) this, partialTick);
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void mizulune$bobHurt(
        PoseStack poseStack,
        float partialTick,
        CallbackInfo callbackInfo
    ) {
        HookDecision<Void> decision = GameRendererHookCallbacks.onBobHurt();
        if (decision.handled()) callbackInfo.cancel();
    }

    @Inject(method = "renderConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void mizulune$renderConfusionOverlay(
        GuiGraphics graphics,
        float scale,
        CallbackInfo callbackInfo
    ) {
        HookDecision<Void> decision = GameRendererHookCallbacks.onRenderConfusionOverlay();
        if (decision.handled()) callbackInfo.cancel();
    }

    @Inject(method = "getProjectionMatrix", at = @At("HEAD"), cancellable = true)
    private void mizulune$getProjectionMatrix(
            double fov,
            CallbackInfoReturnable<Matrix4f> callbackInfo) {
        HookDecision<Matrix4f> decision = GameRendererHookCallbacks.onProjectionMatrix(
                (GameRenderer) (Object) this, fov);
        if (decision.handled()) {
            callbackInfo.setReturnValue(decision.value());
        }
    }
}
