package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Overwrite;
import asm.patchify.annotation.Patch;
import asm.patchify.annotation.WrapInvoke;
import java.util.Map;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.asm.Invocation;
import shit.zen.event.impl.*;
import shit.zen.hook.HookDecision;
import shit.zen.hook.LivingEntityHookCallbacks;
import shit.zen.utils.misc.ReflectionUtil;
import shit.zen.utils.rotation.RotationHandler;

@Patch(LivingEntity.class)
public class LivingEntityPatch {
    @Inject(method = "aiStep", desc = "()V", at = @At(At.Type.HEAD))
    public static void onAiStep(LivingEntity entity, CallbackInfo callbackInfo) {
        LivingEntityHookCallbacks.onAiStep(entity);
    }

    @Overwrite(method = "hasEffect", desc = "(Lnet/minecraft/world/effect/MobEffect;)Z")
    @SuppressWarnings("unchecked")
    public static boolean overwriteHasEffect(LivingEntity entity, MobEffect effect) throws Exception {
        HookDecision<Boolean> decision = LivingEntityHookCallbacks.onHasEffect(entity, effect);
        if (decision.handled()) {
            return decision.value();
        }
        Map<MobEffect, MobEffectInstance> activeEffects =
                (Map<MobEffect, MobEffectInstance>) ReflectionUtil.getStaticField(entity, "activeEffects", "net/minecraft/world/entity/LivingEntity");
        return activeEffects.containsKey(effect);
    }

    @WrapInvoke(method = "tickHeadTurn", desc = "(FF)F", target = "net/minecraft/world/entity/LivingEntity/getYRot", targetDesc = "()F")
    public static float onTickHeadTurn(LivingEntity entity, float yaw, float partial, Invocation<LivingEntity, Float> original) throws Exception {
        float currentYaw = original.call();
        return LivingEntityHookCallbacks.onTickHeadTurnYaw(entity, currentYaw);
    }

    @WrapInvoke(method = "tick", desc = "()V", target = "net/minecraft/world/entity/LivingEntity/getYRot", targetDesc = "()F")
    public static float onTickGetYRot(LivingEntity entity, Invocation<LivingEntity, Float> original) throws Exception {
        return LivingEntityHookCallbacks.onTickYaw(entity, original.call());
    }

    @WrapInvoke(method = "jumpFromGround", desc = "()V", target = "net/minecraft/world/entity/LivingEntity/getYRot", targetDesc = "()F")
    public static float onJumpGetYRot(LivingEntity entity, Invocation<LivingEntity, Float> original) throws Exception {
        float yaw = original.call();
        return LivingEntityHookCallbacks.onJumpYaw(entity, yaw);
    }

    @Inject(method = "jumpFromGround", desc = "()V", at = @At(At.Type.TAIL))
    public static void onAfterJump(LivingEntity entity, CallbackInfo callbackInfo) {
        LivingEntityHookCallbacks.onAfterJump(entity);
    }

    @Inject(method = "travel", desc = "(Lnet/minecraft/world/phys/Vec3;)V", at = @At(At.Type.HEAD))
    public static void onTravel(LivingEntity entity, Vec3 movement, CallbackInfo callbackInfo) throws Exception {
        if (LivingEntityHookCallbacks.onTravel(entity).handled()) {
            callbackInfo.cancel();
        }
    }

    @WrapInvoke(method = "travel", desc = "(Lnet/minecraft/world/phys/Vec3;)V", target = "net/minecraft/world/entity/LivingEntity/getXRot", targetDesc = "()F")
    public static float onTravelGetXRot(LivingEntity entity, Vec3 movement, Invocation<LivingEntity, Float> original) throws Exception {
        float pitch = original.call();
        return LivingEntityHookCallbacks.onFallFlyingPitch(entity, pitch);
    }

    @Inject(method = "hurt", desc = "(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(At.Type.HEAD))
    public static void onHurt(LivingEntity entity, DamageSource source, float amount, CallbackInfo callbackInfo) {
        LivingEntityHookCallbacks.onHurt(entity, source, amount);
    }
}
