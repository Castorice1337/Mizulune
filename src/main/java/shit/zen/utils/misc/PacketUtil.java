package shit.zen.utils.misc;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import lombok.Generated;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;
import net.minecraft.network.protocol.status.ClientboundPongResponsePacket;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.PacketSendEvent;
import shit.zen.utils.misc.ReflectionUtil;

public final class PacketUtil
extends ClientBase {
    public static final ArrayList<Packet<ServerGamePacketListener>> queuedPackets = new ArrayList<>();
    private static final Set<Packet<?>> preparedBufferedPackets =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public static SendPreparation prepareSend(Packet<ServerGamePacketListener> packet) {
        if (removePreparedBufferedMarker(packet)) {
            removeQueuedMarker(packet);
            return new SendPreparation(false, true);
        }
        PacketSendEvent packetSendEvent = new PacketSendEvent(packet);
        ZenClient.getInstance().getEventBus().call(packetSendEvent);
        if (packetSendEvent.isCancelled()) {
            removeQueuedMarker(packet);
            return new SendPreparation(true, false);
        }
        return new SendPreparation(false, removeQueuedMarker(packet));
    }

    public static boolean shouldBypass(Packet<ServerGamePacketListener> packet) {
        SendPreparation preparation = prepareSend(packet);
        return preparation.cancelled() || preparation.bypass();
    }

    private static boolean removeQueuedMarker(Packet<ServerGamePacketListener> packet) {
        synchronized (queuedPackets) {
            return queuedPackets.remove(packet);
        }
    }

    private static boolean removePreparedBufferedMarker(Packet<?> packet) {
        synchronized (preparedBufferedPackets) {
            return preparedBufferedPackets.remove(packet);
        }
    }

    public static void sendPredictive(PredictiveAction predictiveAction) {
        if (mc.getConnection() == null || mc.level == null) {
            return;
        }
        try {
            String fieldName = ReflectionUtil.getMappedFieldName(ClientLevel.class, "blockStatePredictionHandler");
            if (fieldName == null) {
                return;
            }
            Field field = ClientLevel.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            BlockStatePredictionHandler predictionHandler = (BlockStatePredictionHandler)field.get(mc.level);
            try (BlockStatePredictionHandler predicting = predictionHandler.startPredicting()){
                int sequence = predicting.currentSequence();
                mc.getConnection().send(predictiveAction.predict(sequence));
            }
        } catch (Exception ex) {
            logger.error("Failed to send predictive packet", ex);
        }
    }

    public static void sendPredictiveDirect(PredictiveAction predictiveAction) {
        if (mc.getConnection() == null || mc.level == null) {
            return;
        }
        try {
            String fieldName = ReflectionUtil.getMappedFieldName(ClientLevel.class, "blockStatePredictionHandler");
            if (fieldName == null) {
                return;
            }
            Field field = ClientLevel.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            BlockStatePredictionHandler predictionHandler = (BlockStatePredictionHandler)field.get(mc.level);
            try (BlockStatePredictionHandler predicting = predictionHandler.startPredicting()){
                int sequence = predicting.currentSequence();
                PacketUtil.sendQueued(predictiveAction.predict(sequence));
            }
        } catch (Exception ex) {
            logger.error("Failed to send queued predictive packet", ex);
        }
    }

    public static boolean isIgnored(Packet<?> packet) {
        if (mc.player == null) {
            return true;
        }
        if (mc.screen instanceof ReceivingLevelScreen) {
            return true;
        }
        if (packet instanceof ClientboundCustomQueryPacket) {
            return true;
        }
        if (packet instanceof ClientboundTabListPacket) {
            return true;
        }
        if (packet instanceof ClientboundDisconnectPacket) {
            return true;
        }
        if (packet instanceof ClientboundLevelChunkPacketData) {
            return true;
        }
        if (packet instanceof ClientboundForgetLevelChunkPacket) {
            return true;
        }
        if (packet instanceof ClientboundPongResponsePacket) {
            return true;
        }
        if (packet instanceof ClientboundLoginPacket) {
            return true;
        }
        if (packet instanceof ClientboundGameProfilePacket) {
            return true;
        }
        if (packet instanceof ClientboundMapItemDataPacket) {
            return true;
        }
        if (packet instanceof ClientboundSetDefaultSpawnPositionPacket) {
            return true;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket) {
            return true;
        }
        if (packet instanceof ClientboundCustomPayloadPacket) {
            return true;
        }
        if (packet instanceof ClientboundPlayerChatPacket) {
            return true;
        }
        if (packet instanceof ClientboundSetTitleTextPacket) {
            return true;
        }
        return mc.player.tickCount <= 60;
    }

    public static void sendQueued(Packet<ServerGamePacketListener> packet) {
        if (mc.player == null) {
            return;
        }
        synchronized (queuedPackets) {
            queuedPackets.add(packet);
        }
        mc.player.connection.send(packet);
    }

    public static void sendBuffered(
            Packet<ServerGamePacketListener> packet,
            PacketSendListener listener) {
        if (mc.player == null || mc.player.connection == null) {
            return;
        }
        synchronized (queuedPackets) {
            queuedPackets.add(packet);
        }
        synchronized (preparedBufferedPackets) {
            preparedBufferedPackets.add(packet);
        }
        mc.player.connection.getConnection().send(packet, listener);
    }

    public static void send(Packet<ServerGamePacketListener> packet) {
        if (mc.player == null) {
            return;
        }
        mc.player.connection.send(packet);
    }

    @Generated
    private PacketUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record SendPreparation(boolean cancelled, boolean bypass) {
    }
}
