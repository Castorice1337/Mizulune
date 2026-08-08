package shit.zen.fabric.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.LivingEntityHookCallbacks;

/** LivingEntity movement, rotation and damage adapter for 26.2. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void mizulune$aiStep(CallbackInfo callbackInfo) {
        LivingEntityHookCallbacks.onAiStep((LivingEntity) (Object) this);
    }

    @Redirect(
        method = "tickHeadTurn",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F")
    )
    private float mizulune$tickHeadTurnYaw(LivingEntity entity) {
        return LivingEntityHookCallbacks.onTickHeadTurnYaw(
            (LivingEntity) (Object) this, entity.getYRot());
    }

    @Redirect(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F")
    )
    private float mizulune$tickYaw(LivingEntity entity) {
        return LivingEntityHookCallbacks.onTickYaw(
            (LivingEntity) (Object) this, entity.getYRot());
    }

    @Redirect(
        method = "jumpFromGround",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F")
    )
    private float mizulune$jumpYaw(LivingEntity entity) {
        return LivingEntityHookCallbacks.onJumpYaw(
            (LivingEntity) (Object) this, entity.getYRot());
    }

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void mizulune$afterJump(CallbackInfo callbackInfo) {
        LivingEntityHookCallbacks.onAfterJump((LivingEntity) (Object) this);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void mizulune$travel(Vec3 movement, CallbackInfo callbackInfo) {
        if (LivingEntityHookCallbacks.onTravel((LivingEntity) (Object) this).handled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void mizulune$hurt(DamageSource source, CallbackInfo callbackInfo) {
        LivingEntityHookCallbacks.onHurt((LivingEntity) (Object) this, source, 0.0F);
    }
}
