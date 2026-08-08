package shit.zen.hook;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.EntityHurtEvent;
import shit.zen.event.impl.FallFlyingEvent;
import shit.zen.event.impl.JumpEvent;
import shit.zen.event.impl.JumpMarkerEvent;
import shit.zen.event.impl.PlayerAfterJumpEvent;
import shit.zen.event.impl.RotationAnimationEvent;
import shit.zen.modules.impl.movement.NoDelay;
import shit.zen.modules.impl.movement.Scaffold;
import shit.zen.modules.impl.render.FullBright;
import shit.zen.utils.game.PlayerUtil;
import shit.zen.utils.misc.ReflectionUtil;

/** Shared LivingEntity event and rotation semantics for Patchify and Mixin. */
public final class LivingEntityHookCallbacks {
    private LivingEntityHookCallbacks() {
    }

    public static void onAiStep(LivingEntity entity) {
        if (!isLocalPlayer(entity)) {
            return;
        }
        NoDelay noDelay = NoDelay.INSTANCE;
        if (noDelay != null
                && noDelay.isEnabled()
                && noDelay.fastDig.getValue()
                && (Scaffold.INSTANCE == null || !Scaffold.INSTANCE.isEnabled())) {
            ReflectionUtil.setJumpDelay(entity, 0);
        }
    }

    public static HookDecision<Boolean> onHasEffect(LivingEntity entity, MobEffect effect) {
        return entity != null
                && ClientBase.mc != null
                && entity == ClientBase.mc.player
                && effect == MobEffects.NIGHT_VISION
                && FullBright.INSTANCE != null
                && FullBright.INSTANCE.isEnabled()
                ? HookDecision.handled(true)
                : HookDecision.pass();
    }

    public static float onTickHeadTurnYaw(LivingEntity entity, float currentYaw) {
        RotationAnimationEvent event = new RotationAnimationEvent(currentYaw, 0.0f, 0.0f, 0.0f);
        if (isLocalPlayer(entity)) {
            ZenClient.getInstance().getEventBus().call(event);
        }
        return event.getYaw();
    }

    public static float onTickYaw(LivingEntity entity, float originalYaw) {
        return isLocalPlayer(entity) ? ClientBase.yaw : originalYaw;
    }

    public static float onJumpYaw(LivingEntity entity, float originalYaw) {
        if (!isLocalPlayer(entity)) {
            return originalYaw;
        }
        JumpMarkerEvent event = new JumpMarkerEvent(originalYaw);
        ZenClient.getInstance().getEventBus().call(event);
        ClientBase.yaw = event.getYaw();
        return event.getYaw();
    }

    public static void onAfterJump(LivingEntity entity) {
        if (isLocalPlayer(entity)) {
            ZenClient.getInstance().getEventBus().call(new PlayerAfterJumpEvent());
        }
    }

    public static HookDecision<Void> onTravel(LivingEntity entity) {
        if (!isLocalPlayer(entity)) {
            return HookDecision.pass();
        }
        JumpEvent event = new JumpEvent();
        ZenClient.getInstance().getEventBus().call(event);
        if (!event.isCancelled()) {
            return HookDecision.pass();
        }
        PlayerUtil.updateWalkAnim();
        return HookDecision.cancel();
    }

    public static float onFallFlyingPitch(LivingEntity entity, float originalPitch) {
        if (!isLocalPlayer(entity)) {
            return originalPitch;
        }
        FallFlyingEvent event = new FallFlyingEvent(originalPitch);
        ZenClient.getInstance().getEventBus().call(event);
        return event.getPitch();
    }

    public static void onHurt(LivingEntity entity, DamageSource source, float amount) {
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(new EntityHurtEvent(entity, source, amount));
        }
    }

    private static boolean isLocalPlayer(LivingEntity entity) {
        return entity != null
                && ZenClient.isReady()
                && ClientBase.mc != null
                && entity == ClientBase.mc.player;
    }
}
