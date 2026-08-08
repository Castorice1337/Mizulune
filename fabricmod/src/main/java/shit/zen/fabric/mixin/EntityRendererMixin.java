package shit.zen.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.RenderHookCallbacks;

/** Fabric adapter for suppressing vanilla living-entity name tags. */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void mizulune$renderNameTag(
            Entity entity,
            Component component,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo callbackInfo) {
        if (RenderHookCallbacks.onRenderNameTag(entity).handled()) {
            callbackInfo.cancel();
        }
    }
}
