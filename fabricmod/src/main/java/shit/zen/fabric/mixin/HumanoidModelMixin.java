package shit.zen.fabric.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import shit.zen.hook.LivingEntityRenderHookCallbacks;

/** Fabric adapter for silent player model pitch. */
@Mixin(HumanoidModel.class)
abstract class HumanoidModelMixin<T extends LivingEntity> {
    @ModifyVariable(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 4)
    private float mizulune$headPitch(float pitch, T entity) {
        return LivingEntityRenderHookCallbacks.pitch(entity, pitch).getPitch();
    }
}
