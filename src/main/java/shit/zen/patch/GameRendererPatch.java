package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Overwrite;
import asm.patchify.annotation.Patch;
import asm.patchify.annotation.WrapInvoke;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import shit.zen.ZenClient;
import shit.zen.asm.Invocation;
import shit.zen.hook.GameRendererHookCallbacks;
import shit.zen.hook.HookDecision;
import shit.zen.modules.impl.render.AspectRatio;
import shit.zen.modules.impl.render.NoHurtCam;
import shit.zen.modules.impl.render.NoRender;
import shit.zen.utils.misc.ReflectionUtil;

@Patch(GameRenderer.class)
public class GameRendererPatch {
    @Overwrite(method = "getNightVisionScale", desc = "(Lnet/minecraft/world/entity/LivingEntity;F)F")
    public static float overwriteGetNightVisionScale(LivingEntity entity, float partial) {
        return GameRendererHookCallbacks.getNightVisionScale(entity, partial);
    }

    @Inject(
            method = "render",
            desc = "(FJZ)V",
            at = @At(value = At.Type.AFTER_INVOKE, method = "net/minecraft/client/gui/Gui/render", desc = "(Lnet/minecraft/client/gui/GuiGraphics;F)V")
    )
    public static void onRender(GameRenderer gameRenderer, float partialTick, long nanoTime, boolean renderLevel, CallbackInfo callbackInfo) {
        GameRendererHookCallbacks.onRender(gameRenderer, partialTick);
    }

    @WrapInvoke(
            method = "getProjectionMatrix",
            desc = "(D)Lorg/joml/Matrix4f;",
            target = "org/joml/Matrix4f/setPerspective",
            targetDesc = "(FFFF)Lorg/joml/Matrix4f;"
    )
    public static Matrix4f onGetProjectionMatrix(GameRenderer gameRenderer, double fov, Invocation<GameRenderer, Matrix4f> original) throws Exception {
        HookDecision<Matrix4f> decision =
                GameRendererHookCallbacks.onProjectionMatrix(gameRenderer, fov);
        return decision.handled() ? decision.value() : original.call();
    }

    @Inject(method = "bobHurt", desc = "(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At(At.Type.HEAD))
    public static void onBobHurt(GameRenderer gameRenderer, PoseStack poseStack, float partial, CallbackInfo callbackInfo) {
        HookDecision<Void> decision = GameRendererHookCallbacks.onBobHurt();
        if (decision.handled()) callbackInfo.cancel();
    }

    @Inject(method = "renderConfusionOverlay", desc = "(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At(At.Type.HEAD))
    public static void onRenderConfusionOverlay(GameRenderer gameRenderer, GuiGraphics graphics, float scale, CallbackInfo callbackInfo) {
        HookDecision<Void> decision = GameRendererHookCallbacks.onRenderConfusionOverlay();
        if (decision.handled()) callbackInfo.cancel();
    }
}
