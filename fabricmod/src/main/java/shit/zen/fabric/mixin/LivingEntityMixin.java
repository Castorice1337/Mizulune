package shit.zen.fabric.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shit.zen.hook.HookDecision;
import shit.zen.hook.LivingEntityHookCallbacks;

/** Fabric adapter for LivingEntity movement, rotation, jump and hurt events. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void mizulune$aiStep(CallbackInfo callbackInfo) {
        LivingEntityHookCallbacks.onAiStep((LivingEntity) (Object) this);
    }

    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void mizulune$hasEffect(
            MobEffect effect,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        HookDecision<Boolean> decision = LivingEntityHookCallbacks.onHasEffect(
                (LivingEntity) (Object) this, effect);
        if (decision.handled()) {
            callbackInfo.setReturnValue(decision.value());
        }
    }

    @Redirect(
            method = "tickHeadTurn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float mizulune$tickHeadTurnYaw(LivingEntity entity) {
        return LivingEntityHookCallbacks.onTickHeadTurnYaw(
                (LivingEntity) (Object) this, entity.getYRot());
    }

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float mizulune$tickYaw(LivingEntity entity) {
        return LivingEntityHookCallbacks.onTickYaw(
                (LivingEntity) (Object) this, entity.getYRot());
    }

    @Redirect(
            method = "jumpFromGround",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
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

    @Redirect(
            method = "travel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
    private float mizulune$fallFlyingPitch(LivingEntity entity) {
        return LivingEntityHookCallbacks.onFallFlyingPitch(
                (LivingEntity) (Object) this, entity.getXRot());
    }

    @Inject(method = "hurt", at = @At("HEAD"))
    private void mizulune$hurt(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> callbackInfo) {
        LivingEntityHookCallbacks.onHurt((LivingEntity) (Object) this, source, amount);
    }
}
