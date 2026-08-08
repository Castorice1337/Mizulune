package shit.zen.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.ItemInHandRendererHookCallbacks;

/** Fabric adapter for silent held-item rendering and OldHitting. */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mizulune$getMainHandItem(LocalPlayer player) {
        return ItemInHandRendererHookCallbacks.mainHandItem(player);
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void mizulune$renderArmWithItem(
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
                (ItemInHandRenderer) (Object) this,
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
