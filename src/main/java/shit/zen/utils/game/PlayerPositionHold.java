package shit.zen.utils.game;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import shit.zen.ClientBase;
import shit.zen.event.EventPriority;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.event.impl.WorldChangeEvent;
import shit.zen.utils.misc.PacketUtil;

public final class PlayerPositionHold extends ClientBase {
    private static final Map<Object, HoldState> HOLDS = new IdentityHashMap<>();
    private static long sequence;

    public interface DebugSink {
        void onPositionHoldDebug(String phase);
    }

    public static int hold(Object owner, int ticks) {
        if (owner == null || mc.player == null || ticks <= 0) {
            return 0;
        }
        synchronized (HOLDS) {
            HoldState current = HOLDS.get(owner);
            if (current == null) {
                current = new HoldState(owner);
                HOLDS.put(owner, current);
            }
            current.capturePlayer();
            if (current.remainingTicks >= 0) {
                current.remainingTicks = Math.max(current.remainingTicks, ticks);
            }
            current.sequence = ++sequence;
        }
        emit(owner, "hold-pos");
        return ticks;
    }

    public static int extend(Object owner, int ticks) {
        if (owner == null || mc.player == null || ticks <= 0) {
            return 0;
        }
        synchronized (HOLDS) {
            HoldState current = HOLDS.get(owner);
            if (current == null) {
                current = new HoldState(owner);
                current.capturePlayer();
                HOLDS.put(owner, current);
            }
            if (current.remainingTicks >= 0) {
                current.remainingTicks = Math.max(current.remainingTicks, ticks);
            }
            current.sequence = ++sequence;
        }
        emit(owner, "hold-extend");
        return ticks;
    }

    public static void holdUntilRelease(Object owner) {
        if (owner == null || mc.player == null) {
            return;
        }
        synchronized (HOLDS) {
            HoldState current = HOLDS.get(owner);
            if (current == null) {
                current = new HoldState(owner);
                HOLDS.put(owner, current);
            }
            current.capturePlayer();
            current.remainingTicks = -1;
            current.sequence = ++sequence;
        }
        emit(owner, "hold-pos");
    }

    public static void release(Object owner) {
        if (owner == null) {
            return;
        }
        synchronized (HOLDS) {
            HOLDS.remove(owner);
        }
    }

    public static boolean isActive() {
        return activeHold() != null;
    }

    public static boolean isOwnedActive(Object owner) {
        if (owner == null) {
            return false;
        }
        synchronized (HOLDS) {
            return HOLDS.containsKey(owner);
        }
    }

    public static boolean hasExternalHold(Object owner) {
        synchronized (HOLDS) {
            for (Object holdOwner : HOLDS.keySet()) {
                if (holdOwner != owner) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int remainingTicks(Object owner) {
        if (owner == null) {
            return 0;
        }
        synchronized (HOLDS) {
            HoldState state = HOLDS.get(owner);
            return state == null ? 0 : state.remainingTicks;
        }
    }

    public static float normalizeYawForBypass(float yaw) {
        return yaw > -360.0f && yaw < 360.0f ? yaw + 720.0f : yaw;
    }

    @EventTarget(EventPriority.HIGHEST)
    public void onTick(TickEvent event) {
        if (mc.player == null) {
            synchronized (HOLDS) {
                HOLDS.clear();
            }
            return;
        }
        synchronized (HOLDS) {
            HOLDS.entrySet().removeIf(entry -> {
                HoldState state = entry.getValue();
                if (state.remainingTicks < 0) {
                    return false;
                }
                state.remainingTicks--;
                return state.remainingTicks <= 0;
            });
        }
    }

    @EventTarget(EventPriority.LOWEST)
    public void onMotion(MotionEvent event) {
        HoldState state = activeHold();
        if (state == null || mc.player == null || !event.isPost()) {
            return;
        }
        event.setX(state.x);
        event.setY(state.y);
        event.setZ(state.z);
        event.setOnGround(state.onGround);
        mc.player.setDeltaMovement(0.0, 0.0, 0.0);
        mc.player.setPos(state.x, state.y, state.z);
    }

    @EventTarget(EventPriority.LOWEST)
    public void onStrafe(StrafeEvent event) {
        if (!isActive()) {
            return;
        }
        event.setForward(0.0f);
        event.setStrafe(0.0f);
        event.setSprinting(false);
    }

    @EventTarget(EventPriority.HIGH)
    public void onPacket(PacketEvent event) {
        if (!event.isIncomingRaw() || mc.player == null) {
            return;
        }
        HoldState state = activeHold();
        if (state == null || !(event.getPacket() instanceof ServerboundMovePlayerPacket movePacket)) {
            return;
        }
        if (!movePacket.hasPosition()) {
            emit(state.owner, movePacket.hasRotation() ? "rot-pass" : "status-pass");
            return;
        }
        if (movePacket.hasRotation()) {
            float yaw = normalizeYawForBypass(movePacket.getYRot(mc.player.getYRot()));
            float pitch = movePacket.getXRot(mc.player.getXRot());
            PacketUtil.sendQueued(new ServerboundMovePlayerPacket.Rot(yaw, pitch, movePacket.isOnGround()));
            emit(state.owner, "posrot-convert");
        } else {
            emit(state.owner, "pos-block");
        }
        event.setCancelled(true);
    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent event) {
        synchronized (HOLDS) {
            HOLDS.clear();
        }
    }

    private static HoldState activeHold() {
        synchronized (HOLDS) {
            HoldState best = null;
            for (HoldState state : HOLDS.values()) {
                if (best == null || state.sequence > best.sequence) {
                    best = state;
                }
            }
            return best;
        }
    }

    private static void emit(Object owner, String phase) {
        if (owner instanceof DebugSink sink) {
            sink.onPositionHoldDebug(phase);
        }
    }

    private static final class HoldState {
        private final Object owner;
        private double x;
        private double y;
        private double z;
        private boolean onGround;
        private int remainingTicks;
        private long sequence;

        private HoldState(Object owner) {
            this.owner = owner;
        }

        private void capturePlayer() {
            this.x = mc.player.getX();
            this.y = mc.player.getY();
            this.z = mc.player.getZ();
            this.onGround = mc.player.onGround();
        }
    }
}
