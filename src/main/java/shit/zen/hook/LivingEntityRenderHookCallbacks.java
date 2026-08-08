package shit.zen.hook;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.CameraPitchEvent;
import shit.zen.event.impl.RenderEntityEvent;
import shit.zen.event.impl.RotationAnimationEvent;

/** Shared living-entity model/render rotation semantics. */
public final class LivingEntityRenderHookCallbacks {
    private LivingEntityRenderHookCallbacks() {
    }

    public static CameraPitchEvent pitch(LivingEntity entity, float pitch) {
        if (ZenClient.isReady()
                && ClientBase.mc != null
                && entity == ClientBase.mc.player
                && ClientBase.mc.level != null) {
            return (CameraPitchEvent) ZenClient.getInstance().getEventBus().call(
                    new CameraPitchEvent(pitch));
        }
        return new CameraPitchEvent(pitch);
    }

    public static HookDecision<Void> onRenderPre(
            EntityRenderer<?> renderer,
            LivingEntity entity,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            float partialTick,
            int packedLight) {
        if (!ZenClient.isReady()) {
            return HookDecision.pass();
        }
        RenderEntityEvent.Pre event = new RenderEntityEvent.Pre(
                renderer, entity, poseStack, bufferSource, partialTick, packedLight);
        ZenClient.getInstance().getEventBus().call(event);
        return event.isCancelled() ? HookDecision.cancel() : HookDecision.pass();
    }

    public static void onRenderPost(
            EntityRenderer<?> renderer,
            LivingEntity entity,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            float partialTick,
            int packedLight) {
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(new RenderEntityEvent.Post(
                    renderer, entity, poseStack, bufferSource, partialTick, packedLight));
        }
    }

    public static float headYaw(
            LivingEntity entity,
            float delta,
            float start,
            float end) {
        RotationAnimationEvent event = new RotationAnimationEvent(end, start, 0.0f, 0.0f);
        if (isLocalPlayer(entity)) {
            ZenClient.getInstance().getEventBus().call(event);
        }
        return Mth.rotLerp(delta, event.getLastYaw(), event.getYaw());
    }

    public static float pitch(
            LivingEntity entity,
            float delta,
            float start,
            float end) {
        RotationAnimationEvent event = new RotationAnimationEvent(0.0f, 0.0f, end, start);
        if (isLocalPlayer(entity)) {
            ZenClient.getInstance().getEventBus().call(event);
        }
        return Mth.lerp(delta, event.getLastPitch(), event.getPitch());
    }

    private static boolean isLocalPlayer(LivingEntity entity) {
        return entity != null
                && ZenClient.isReady()
                && ClientBase.mc != null
                && entity == ClientBase.mc.player;
    }
}
