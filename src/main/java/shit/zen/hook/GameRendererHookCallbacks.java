package shit.zen.hook;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.GlRenderEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.modules.impl.render.FullBright;
import shit.zen.modules.impl.render.AspectRatio;
import shit.zen.modules.impl.render.NoHurtCam;
import shit.zen.modules.impl.render.NoRender;
import shit.zen.render.Renderer;
import shit.zen.utils.misc.ReflectionUtil;

/** Shared HUD/render callbacks kept independent from Patchify and Mixin callback types. */
public final class GameRendererHookCallbacks {
    private GameRendererHookCallbacks() {
    }

    public static float getNightVisionScale(LivingEntity entity, float partialTick) {
        HookDecision<Float> fullBright = onFullBrightScale();
        if (fullBright.handled()) {
            return fullBright.value();
        }
        return entity.hasEffect(MobEffects.NIGHT_VISION) ? 1.0f : 0.0f;
    }

    public static HookDecision<Float> onFullBrightScale() {
        return FullBright.INSTANCE != null && FullBright.INSTANCE.isEnabled()
                ? HookDecision.handled(
                        FullBright.INSTANCE.brightnessSetting.getValue().floatValue() / 100.0f)
                : HookDecision.pass();
    }

    public static void onRender(GameRenderer gameRenderer, float partialTick) {
        Minecraft minecraft = ClientBase.mc;
        if (minecraft == null) return;
        GuiGraphics graphics = new GuiGraphics(
            minecraft,
            minecraft.renderBuffers().bufferSource()
        );
        Render2DEvent event = new Render2DEvent(graphics.pose(), graphics, partialTick);
        if (!ZenClient.isReady()) return;
        Renderer.captureCleanBackdrop(graphics);
        ZenClient.getInstance().getEventBus().call(event);
        graphics.pose().pushPose();
        try {
            Renderer.render(graphics, drawContext -> {
                drawContext.beforeExternalGlDraw();
                try {
                    GlRenderEvent glRender = new GlRenderEvent(graphics, graphics.pose(), drawContext);
                    ZenClient.getInstance().getEventBus().call(glRender);
                } finally {
                    drawContext.afterExternalGlDraw();
                }
            });
        } finally {
            graphics.pose().popPose();
        }
    }

    public static HookDecision<Void> onBobHurt() {
        return ZenClient.isReady()
            && NoHurtCam.INSTANCE != null
            && NoHurtCam.INSTANCE.isEnabled()
            ? HookDecision.cancel()
            : HookDecision.pass();
    }

    public static HookDecision<Void> onRenderConfusionOverlay() {
        return NoRender.shouldHideNausea() ? HookDecision.cancel() : HookDecision.pass();
    }

    public static HookDecision<Matrix4f> onProjectionMatrix(
            GameRenderer gameRenderer,
            double requestedFov) {
        boolean useFixedFov = ZenClient.isReady() && NoRender.shouldUseFixedFov();
        boolean useAspectRatio = ZenClient.isReady()
                && AspectRatio.INSTANCE != null
                && AspectRatio.INSTANCE.isEnabled();
        if (!useFixedFov && !useAspectRatio) {
            return HookDecision.pass();
        }

        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().identity();
        float zoom = (Float) ReflectionUtil.getStaticField(
                gameRenderer, "zoom", "net/minecraft/client/renderer/GameRenderer");
        float zoomX = (Float) ReflectionUtil.getStaticField(
                gameRenderer, "zoomX", "net/minecraft/client/renderer/GameRenderer");
        float zoomY = (Float) ReflectionUtil.getStaticField(
                gameRenderer, "zoomY", "net/minecraft/client/renderer/GameRenderer");
        if (zoom != 1.0f) {
            poseStack.translate(zoomX, -zoomY, 0.0f);
            poseStack.scale(zoom, zoom, 1.0f);
        }

        Minecraft minecraft = ClientBase.mc;
        float vanillaAspect = minecraft == null || minecraft.getWindow().getHeight() == 0
                ? 1.0f
                : (float) minecraft.getWindow().getWidth() / (float) minecraft.getWindow().getHeight();
        float aspect = useAspectRatio
                ? AspectRatio.INSTANCE.ratioSetting.getValue().floatValue()
                : vanillaAspect;
        float fov = (float) Math.toRadians(
                useFixedFov ? NoRender.getFixedFov() : requestedFov);
        poseStack.last().pose().mul(new Matrix4f().setPerspective(
                fov,
                aspect,
                0.05f,
                gameRenderer.getDepthFar()));
        return HookDecision.handled(poseStack.last().pose());
    }
}
