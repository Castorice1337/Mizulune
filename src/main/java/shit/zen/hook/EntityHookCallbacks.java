package shit.zen.hook;

import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.PreTickEvent;
import shit.zen.event.impl.RayTraceEvent;
import shit.zen.event.impl.RotationEvent;
import shit.zen.event.impl.SafeWalkEvent;
import shit.zen.event.impl.SneakEvent;
import shit.zen.event.impl.StuckInBlockEvent;
import shit.zen.utils.misc.ReflectionUtil;

/** Shared move-relative semantics for Patchify and Mixin. */
public final class EntityHookCallbacks {
    private EntityHookCallbacks() {
    }

    public static HookDecision<Boolean> onIsStayingOnGroundSurface(Entity entity) {
        if (!isLocalPlayer(entity)) {
            return HookDecision.pass();
        }
        SafeWalkEvent event = new SafeWalkEvent(entity.onGround() && entity.isShiftKeyDown());
        ZenClient.getInstance().getEventBus().call(event);
        return event.isModified()
                ? HookDecision.handled(event.isSafeWalk())
                : HookDecision.pass();
    }

    public static void onMakeStuckInBlock(Entity entity, BlockState state, Vec3 motion) {
        if (!isLocalPlayer(entity)) {
            return;
        }
        StuckInBlockEvent event = new StuckInBlockEvent(state, motion);
        ZenClient.getInstance().getEventBus().call(event);
        ReflectionUtil.setInstanceField(
                entity,
                event.isCancelled() ? Vec3.ZERO : event.getMotion(),
                "stuckSpeedMultiplier",
                "net/minecraft/world/entity/Entity");
    }

    public static HookDecision<Void> onPush(Entity entity) {
        if (!isLocalPlayer(entity) || entity.isInWater()) {
            return HookDecision.pass();
        }
        SneakEvent event = new SneakEvent();
        ZenClient.getInstance().getEventBus().call(event);
        return event.isCancelled() ? HookDecision.cancel() : HookDecision.pass();
    }

    public static void moveRelative(Entity entity, float speed, Vec3 movement) {
        boolean localPlayer = ZenClient.isReady() && entity == ClientBase.mc.player;
        RotationEvent event = new RotationEvent(entity.getYRot(), speed);
        if (localPlayer) {
            ZenClient.getInstance().getEventBus().call(event);
        }

        Vec3 result = applyRotation(movement, speed, event.getYaw());
        entity.setDeltaMovement(entity.getDeltaMovement().add(result));

        if (localPlayer) {
            ZenClient.getInstance().getEventBus().call(new PreTickEvent());
        }
    }

    public static Vec3 applyRotation(Vec3 movement, float speed, float yaw) {
        double lengthSq = movement.lengthSqr();
        if (lengthSq < 1.0e-7) {
            return Vec3.ZERO;
        }
        Vec3 normalized = (lengthSq > 1.0 ? movement.normalize() : movement).scale(speed);
        float sinYaw = Mth.sin(yaw * (float) (Math.PI / 180.0));
        float cosYaw = Mth.cos(yaw * (float) (Math.PI / 180.0));
        return new Vec3(
                normalized.x * cosYaw - normalized.z * sinYaw,
                normalized.y,
                normalized.z * cosYaw + normalized.x * sinYaw);
    }

    public static Vec3 calculateViewVector(Entity entity, float pitch, float yaw) {
        RayTraceEvent event = new RayTraceEvent(entity, yaw, pitch);
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(event);
        }
        float pitchRad = event.getPitch() * (float) (Math.PI / 180.0);
        float yawRad = -event.getYaw() * (float) (Math.PI / 180.0);
        float cosYaw = Mth.cos(yawRad);
        float sinYaw = Mth.sin(yawRad);
        float cosPitch = Mth.cos(pitchRad);
        float sinPitch = Mth.sin(pitchRad);
        return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    private static boolean isLocalPlayer(Entity entity) {
        return entity != null
                && ZenClient.isReady()
                && ClientBase.mc != null
                && entity == ClientBase.mc.player;
    }
}
