package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import asm.patchify.annotation.WrapInvoke;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.asm.Invocation;
import shit.zen.event.impl.UpdateHeldItemEvent;
import shit.zen.modules.impl.render.OldHitting;
import shit.zen.hook.ItemInHandRendererHookCallbacks;

@Patch(ItemInHandRenderer.class)
public class ItemInHandRendererPatch {
    @WrapInvoke(
            method = "tick",
            desc = "()V",
            target = "net/minecraft/client/player/LocalPlayer/getMainHandItem",
            targetDesc = "()Lnet/minecraft/world/item/ItemStack;"
    )
    public static ItemStack onGetMainHandItem(ItemInHandRenderer renderer, Invocation<LocalPlayer, ItemStack> original) {
        return ItemInHandRendererHookCallbacks.mainHandItem(original.instance());
    }

    @Inject(
            method = "renderArmWithItem",
            desc = "(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRenderArmWithItem(
            ItemInHandRenderer renderer,
            AbstractClientPlayer player,
            float partialTicks,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equippedProgress,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo callbackInfo) {
        if (ItemInHandRendererHookCallbacks.onRenderArmWithItem(
                renderer,
                player,
                hand,
                swingProgress,
                stack,
                equippedProgress,
                poseStack,
                bufferSource,
                packedLight).handled()) {
            callbackInfo.cancel();
        }
    }
}
