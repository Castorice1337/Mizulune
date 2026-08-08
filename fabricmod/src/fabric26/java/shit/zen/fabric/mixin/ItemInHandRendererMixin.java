package shit.zen.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.ItemInHandRendererHookCallbacks;

/** Fabric 26.2 adapter for held-item replacement and the original OldHitting transform. */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mizulune$mainHandItem(LocalPlayer player) {
        return ItemInHandRendererHookCallbacks.mainHandItem(player);
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void mizulune$submitArmWithItem(
            AbstractClientPlayer player,
            float frameInterp,
            float xRot,
            InteractionHand hand,
            float attack,
            ItemStack stack,
            float inverseArmHeight,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            CallbackInfo callbackInfo) {
        if (ItemInHandRendererHookCallbacks.onSubmitArmWithItem(
                (ItemInHandRenderer) (Object) this,
                player,
                hand,
                attack,
                stack,
                inverseArmHeight,
                poseStack,
                submitNodeCollector,
                lightCoords).handled()) {
            callbackInfo.cancel();
        }
    }
}
