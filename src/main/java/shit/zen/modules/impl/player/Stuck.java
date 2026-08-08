package shit.zen.modules.impl.player;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import shit.zen.ZenClient;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.event.impl.WorldChangeEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.movement.Scaffold;
import shit.zen.value.impl.BooleanValue;
import shit.zen.value.impl.ModeValue;
import shit.zen.utils.game.PlayerPositionHold;
import shit.zen.utils.misc.ChatUtil;
import shit.zen.utils.misc.PacketUtil;
import shit.zen.utils.rotation.Rotation;
import shit.zen.platform.ItemCompat;
import shit.zen.utils.rotation.RotationHandler;
import shit.zen.event.EventTarget;

public class Stuck
extends Module implements PlayerPositionHold.DebugSink {
    public static Stuck INSTANCE;
    private static final int DISABLE_RELEASE_TICKS = 2;
    private final ModeValue ModeValue = new ModeValue("Mode", "Delay", "Packet").withDefault("Delay");
    private final BooleanValue debug = new BooleanValue("Debug", false);
    private int stuckState = 0;
    private Packet<?> capturedPacket;
    private float savedYaw;
    private float savedPitch;
    private boolean pendingDisable = false;
    private int disableDeadlineTick = -1;
    private int holdDebugTicks = 0;
    private String lastHoldDebugState;
    private final Queue<ServerboundPongPacket> pongQueue = new ConcurrentLinkedQueue<>();

    public Stuck() {
        super("Stuck", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.stuckState = 0;
        this.capturedPacket = null;
        Rotation rotation = RotationHandler.targetRotation != null
                ? RotationHandler.targetRotation
                : new Rotation(mc.player.getYRot(), mc.player.getXRot());
        this.savedYaw = rotation.getYaw();
        this.savedPitch = rotation.getPitch();
        this.pendingDisable = false;
        this.disableDeadlineTick = -1;
        this.holdDebugTicks = 0;
        this.lastHoldDebugState = null;
        PlayerPositionHold.release(this);
        PlayerPositionHold.holdUntilRelease(this);
    }

    @Override
    public void setEnabled(boolean enable) {
        if (mc.player == null) {
            if (!enable) {
                this.finishDisable(false);
            }
            return;
        }
        if (enable) {
            super.setEnabled(true);
            return;
        }
        if (!this.isEnabled()) {
            this.finishDisable(false);
            return;
        }
        if (!this.ModeValue.is("Delay") || this.stuckState == 3) {
            this.finishDisable(false);
            return;
        }
        this.pendingDisable = true;
        this.disableDeadlineTick = mc.player.tickCount + DISABLE_RELEASE_TICKS;
    }

    @Override
    public void onDisable() {
        this.pendingDisable = false;
        this.disableDeadlineTick = -1;
        this.capturedPacket = null;
        this.stuckState = 3;
        PlayerPositionHold.release(this);
        super.onDisable();
    }

    @EventTarget
    public void onTick(TickEvent tickEvent) {
        if (this.pendingDisable && this.disableDeadlineTick >= 0
                && mc.player != null && mc.player.tickCount >= this.disableDeadlineTick) {
            this.finishDisable(true);
            return;
        }
        if (!this.ModeValue.is("Packet")) {
            return;
        }
        Scaffold scaffold = Scaffold.INSTANCE;
        if (scaffold.isEnabled()) {
            scaffold.setEnabled(false);
            return;
        }
        if (mc.player == null) {
            return;
        }
        if (!this.isAntiVoidActive()) {
            PacketUtil.sendQueued(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }

    private boolean isAntiVoidActive() {
        return ZenClient.isReady() && AntiVoid.INSTANCE != null && AntiVoid.INSTANCE.isEnabled() && !mc.player.onGround() && AntiVoid.INSTANCE.bufferingPackets;
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        Scaffold scaffold = Scaffold.INSTANCE;
        if (!this.isAntiVoidActive() && scaffold.isEnabled()) {
            scaffold.setEnabled(false);
            return;
        }
        if (mc.player == null) {
            return;
        }
        if (motionEvent.isPost()) {
            mc.player.setDeltaMovement(0.0, 0.0, 0.0);
            if (this.stuckState == 1) {
                this.stuckState = 2;
                float currentYaw = mc.player.getYRot();
                float currentPitch = mc.player.getXRot();
                if (this.shouldSendCapturedPacket() && (this.savedYaw != currentYaw || this.savedPitch != currentPitch)) {
                    PacketUtil.sendQueued(new ServerboundMovePlayerPacket.Rot(currentYaw, currentPitch, mc.player.onGround()));
                    while (!this.pongQueue.isEmpty()) {
                        PacketUtil.sendQueued(this.pongQueue.poll());
                    }
                    this.savedYaw = currentYaw;
                    this.savedPitch = currentPitch;
                }
                PacketUtil.sendQueued(this.capturedPacket);
            } else if (!this.isAntiVoidActive() && this.ModeValue.is("Packet") && mc.player.tickCount % 10 == 0) {
                while (!this.pongQueue.isEmpty()) {
                    PacketUtil.sendQueued(this.pongQueue.poll());
                }
            }
            if (this.pendingDisable) {
                this.finishDisable(true);
            }
        }
    }

    private void finishDisable(boolean sendReleasePacket) {
        PlayerPositionHold.release(this);
        if (mc.player != null && sendReleasePacket) {
            if (this.ModeValue.is("Delay")) {
                PacketUtil.sendQueued(new ServerboundMovePlayerPacket.Pos(
                        mc.player.getX() + 1337.0, mc.player.getY(), mc.player.getZ() + 1337.0,
                        mc.player.onGround()));
            } else {
                PacketUtil.sendQueued(new ServerboundPlayerCommandPacket(
                        mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            }
        }
        while (!this.pongQueue.isEmpty()) {
            PacketUtil.sendQueued(this.pongQueue.poll());
        }
        this.stuckState = 3;
        this.capturedPacket = null;
        this.pendingDisable = false;
        this.disableDeadlineTick = -1;
        if (this.isEnabled()) {
            super.setEnabled(false);
        }
    }

    private boolean shouldSendCapturedPacket() {
        if (this.capturedPacket instanceof ServerboundUseItemPacket useItemPacket) {
            ItemStack heldStack = mc.player.getItemInHand(useItemPacket.getHand());
            return !ItemCompat.isBowlFood(heldStack.getItem()) && !(heldStack.getItem() instanceof BowItem);
        }
        if (this.capturedPacket instanceof ServerboundPlayerActionPacket actionPacket) {
            return actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM && mc.player.getUseItem().getItem() instanceof BowItem;
        }
        return false;
    }

    @EventTarget
    public void onStrafe(StrafeEvent strafeEvent) {
        strafeEvent.setForward(0.0f);
        strafeEvent.setStrafe(0.0f);
        strafeEvent.setSprinting(false);
    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent worldChangeEvent) {
        this.finishDisable(false);
    }

    @EventTarget(value=1)
    public void onPacket(PacketEvent packetEvent) {
        if (mc.player == null) {
            return;
        }
        Object rawPacket = packetEvent.getPacket();
        if (rawPacket instanceof ServerboundMovePlayerPacket) {
            return;
        } else if (packetEvent.getPacket() instanceof ServerboundPongPacket) {
            this.pongQueue.offer((ServerboundPongPacket)packetEvent.getPacket());
            packetEvent.setCancelled(true);
        } else if (packetEvent.getPacket() instanceof ServerboundUseItemPacket || packetEvent.getPacket() instanceof ServerboundPlayerActionPacket) {
            this.capturedPacket = packetEvent.getPacket();
            this.stuckState = 1;
            packetEvent.setCancelled(true);
        } else if (packetEvent.getPacket() instanceof ClientboundPlayerPositionPacket && this.ModeValue.is("Delay")) {
            while (!this.pongQueue.isEmpty()) {
                PacketUtil.sendQueued(this.pongQueue.poll());
            }
            this.finishDisable(false);
        }
    }

    @Override
    public void onPositionHoldDebug(String phase) {
        if (!this.debug.getValue() || mc.player == null) {
            return;
        }
        String state = phase
                + " mode=" + this.ModeValue.getValue()
                + " hold=" + PlayerPositionHold.remainingTicks(this)
                + " state=" + this.stuckState
                + " pending=" + this.pendingDisable;
        this.holdDebugTicks++;
        if (state.equals(this.lastHoldDebugState) && this.holdDebugTicks % 5 != 0) {
            return;
        }
        this.lastHoldDebugState = state;
        String line = "[StuckDebug] tick=" + mc.player.tickCount + " " + state;
        logger.info(line);
        ChatUtil.print(line);
    }
}
