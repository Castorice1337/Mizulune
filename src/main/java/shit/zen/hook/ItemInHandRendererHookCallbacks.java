package shit.zen.hook;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.UpdateHeldItemEvent;
import shit.zen.modules.impl.render.OldHitting;

/** Shared first-person held-item semantics for Patchify and Mixin. */
public final class ItemInHandRendererHookCallbacks {
    private ItemInHandRendererHookCallbacks() {
    }

    public static ItemStack mainHandItem(LivingEntity entity) {
        ItemStack original = entity == null ? ItemStack.EMPTY : entity.getMainHandItem();
        UpdateHeldItemEvent event = new UpdateHeldItemEvent(InteractionHand.MAIN_HAND, original);
        if (ZenClient.isReady()
                && ClientBase.mc != null
                && entity == ClientBase.mc.player) {
            ZenClient.getInstance().getEventBus().call(event);
        }
        return event.getItemStack();
    }

    public static HookDecision<Void> onRenderArmWithItem(
            ItemInHandRenderer renderer,
            AbstractClientPlayer player,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equippedProgress,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        if (!ZenClient.isReady()
                || ClientBase.mc == null
                || ClientBase.mc.player == null
                || OldHitting.INSTANCE == null
                || !OldHitting.INSTANCE.isEnabled()) {
            return HookDecision.pass();
        }
        if (!OldHitting.INSTANCE.shouldApply(hand, stack)) {
            return HookDecision.pass();
        }

        OldHitting.INSTANCE.applyHitAnimation(
                poseStack,
                swingProgress,
                player.getMainArm(),
                equippedProgress);
        boolean rightHand = player.getMainArm() == HumanoidArm.RIGHT;
        renderer.renderItem(
                player,
                stack,
                rightHand
                        ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                        : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !rightHand,
                poseStack,
                bufferSource,
                packedLight);
        return HookDecision.cancel();
    }
}
