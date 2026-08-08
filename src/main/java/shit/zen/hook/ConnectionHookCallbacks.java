package shit.zen.hook;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.PrePacketEvent;
import shit.zen.modules.impl.movement.Scaffold;
import shit.zen.modules.impl.movement.scaffold.debug.ScaffoldTraceRecorder;
import shit.zen.modules.impl.world.Protocol;
import shit.zen.utils.misc.PacketUtil;
import shit.zen.utils.rotation.RotationHandler;

/** Shared network interception semantics used by ASM and Fabric Mixin adapters. */
public final class ConnectionHookCallbacks {
    private ConnectionHookCallbacks() {
    }

    public static boolean onPacketReceive(Connection sourceConnection, Packet<?> packet) {
        if (packet == null || !isClientConnection(sourceConnection)) return false;
        CarriedItemSync.onIncomingPacket(sourceConnection, packet);
        tracePlacementResponse(packet);
        ZenClient client = ZenClient.getInstance();
        Protocol protocol = client == null ? null : client.getProtocolModule();
        if (protocol != null && protocol.consumeEarlyPacket(sourceConnection, packet)) {
            return true;
        }
        if (ClientBase.mc == null
            || ClientBase.mc.level == null
            || ClientBase.mc.player == null
            || !ZenClient.isReady()) {
            return false;
        }
        try {
            ScaffoldTraceRecorder.recordIncoming(packet);
        } catch (Throwable ignored) {
        }
        PrePacketEvent prePacket = new PrePacketEvent(packet);
        client.getEventBus().call(prePacket);
        if (prePacket.isCancelled()) {
            return true;
        }
        PacketEvent event = new PacketEvent(prePacket.getPacket(), false, sourceConnection);
        client.getEventBus().call(event);
        return event.isCancelled();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean onPacketSend(
            Connection sourceConnection,
            Packet<?> packet,
            PacketSendListener listener) {
        if (!isClientConnection(sourceConnection)) return false;
        if (ClientBase.mc == null
            || ClientBase.mc.level == null
            || ClientBase.mc.player == null
            || packet == null
            || !ZenClient.isReady()) {
            return false;
        }
        PacketUtil.SendPreparation preparation = PacketUtil.prepareSend((Packet) packet);
        if (preparation.cancelled()) {
            traceUseItemOn(packet, "cancelled-by-prepareSend");
            return true;
        }
        if (!preparation.bypass()) {
            PacketEvent event = new PacketEvent(packet, true);
            ZenClient.getInstance().getEventBus().call(event);
            if (event.isCancelled()) {
                traceUseItemOn(packet, "cancelled-by-PacketEvent");
                return true;
            }
        }
        CarriedItemSync.beforeOutgoingInteraction(sourceConnection, packet);
        boolean intercepted = Scaffold.interceptOutgoingPacket(packet, listener);
        CarriedItemSync.onOutgoingPacketAccepted(sourceConnection, packet);
        RotationHandler.onOutgoingPacketAccepted(packet);
        traceUseItemOn(packet, intercepted ? "intercepted-by-Scaffold" : "accepted");
        return intercepted;
    }

    private static void traceUseItemOn(Packet<?> packet, String outcome) {
        if (MultiPlayerGameModeHookCallbacks.isTraceEnabled()
                && packet instanceof ServerboundUseItemOnPacket useItemOn) {
            ClientBase.logger.info(
                    "[InteractionTrace] outbound useItemOn outcome={} hand={} selected={} "
                            + "acceptedServerSlot={} finalServerSlot={} block={} face={} sequence={}",
                    outcome,
                    useItemOn.getHand(),
                    ClientBase.mc == null || ClientBase.mc.player == null
                            ? null : ClientBase.mc.player.getInventory().selected,
                    CarriedItemSync.acceptedServerSlot(),
                    CarriedItemSync.finalServerSlot(),
                    useItemOn.getHitResult().getBlockPos(),
                    useItemOn.getHitResult().getDirection(),
                    useItemOn.getSequence());
        } else if (MultiPlayerGameModeHookCallbacks.isTraceEnabled()
                && packet instanceof ServerboundSetCarriedItemPacket carriedItem) {
            ClientBase.logger.info(
                    "[InteractionTrace] outbound carriedItem outcome={} slot={} acceptedServerSlot={} finalServerSlot={}",
                    outcome,
                    carriedItem.getSlot(),
                    CarriedItemSync.acceptedServerSlot(),
                    CarriedItemSync.finalServerSlot());
        }
    }

    private static void tracePlacementResponse(Packet<?> packet) {
        if (!MultiPlayerGameModeHookCallbacks.isTraceEnabled()) {
            return;
        }
        if (packet instanceof ClientboundBlockChangedAckPacket ack) {
            ClientBase.logger.info("[InteractionTrace] inbound blockAck sequence={}", ack.sequence());
        } else if (packet instanceof ClientboundBlockUpdatePacket update) {
            ClientBase.logger.info(
                    "[InteractionTrace] inbound blockUpdate block={} state={}",
                    update.getPos(),
                    update.getBlockState());
        }
    }

    public static void traceMovePacket(Packet<?> packet, String phase) {
        if (MultiPlayerGameModeHookCallbacks.isTraceEnabled()
                && packet instanceof ServerboundMovePlayerPacket move
                && move.hasPosition()) {
            ClientBase.logger.info(
                    "[InteractionTrace] move {} pos=({},{},{}) rot=({},{}) onGround={}",
                    phase,
                    move.getX(Double.NaN),
                    move.getY(Double.NaN),
                    move.getZ(Double.NaN),
                    move.getYRot(Float.NaN),
                    move.getXRot(Float.NaN),
                    move.isOnGround());
        }
    }

    public static void captureScaffoldTraceContext(
            Connection sourceConnection,
            Packet<?> packet) {
        if (!isClientConnection(sourceConnection)) return;
        try {
            ScaffoldTraceRecorder.captureCurrentContext(packet);
        } catch (Throwable ignored) {
        }
    }

    public static void onFinalPacketWrite(
            Connection sourceConnection,
            Packet<?> packet) {
        if (!isClientConnection(sourceConnection)) return;
        CarriedItemSync.onFinalPacketWrite(sourceConnection, packet);
        traceMovePacket(packet, "final-write");
        try {
            ZenClient client = ZenClient.getInstance();
            Protocol protocol = client == null ? null : client.getProtocolModule();
            if (protocol != null) protocol.onFinalPacketWrite(packet);
        } catch (Throwable ignored) {
        }
        try {
            RotationHandler.onFinalPacketWrite(packet);
        } catch (Throwable ignored) {
        }
        try {
            ScaffoldTraceRecorder.recordFinalWrite(packet);
        } catch (Throwable ignored) {
        }
    }

    public static boolean isClientConnection(Connection connection) {
        return connection != null && connection.getReceiving() == PacketFlow.CLIENTBOUND;
    }
}
