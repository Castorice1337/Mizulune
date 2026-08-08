package shit.zen.hook;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.UpdateHeldItemEvent;
import shit.zen.modules.impl.render.OldHitting;

/** Fabric 26.2 submission adapter for the shared first-person held-item semantics. */
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

    public static HookDecision<Void> onSubmitArmWithItem(
            ItemInHandRenderer renderer,
            AbstractClientPlayer player,
            InteractionHand hand,
            float attack,
            ItemStack stack,
            float inverseArmHeight,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords) {
        if (!ZenClient.isReady()
                || OldHitting.INSTANCE == null
                || !OldHitting.INSTANCE.shouldApply(hand, stack)) {
            return HookDecision.pass();
        }

        OldHitting.INSTANCE.applyHitAnimation(
                poseStack,
                attack,
                player.getMainArm(),
                inverseArmHeight);
        boolean rightHand = player.getMainArm() == HumanoidArm.RIGHT;
        renderer.renderItem(
                player,
                stack,
                rightHand
                        ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                        : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                poseStack,
                submitNodeCollector,
                lightCoords);
        return HookDecision.cancel();
    }
}
