package shit.zen.hook;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import shit.zen.ClientBase;
import shit.zen.utils.misc.PacketUtil;
import shit.zen.utils.misc.ReflectionUtil;

/**
 * Reconciles vanilla's cached carried slot with direct/silent slot packets.
 *
 * <p>The server slot and the visible client slot are allowed to differ inside a short
 * silent-switch transaction. Outside that transaction, an interaction gets one ordered
 * corrective slot packet before it reaches the wire.</p>
 */
public final class CarriedItemSync {
    private static final int UNKNOWN_SLOT = -1;
    private static final int NO_TICK = Integer.MIN_VALUE;
    private static final int SILENT_INTENT_MAX_AGE_TICKS = 2;

    private static final Map<Packet<?>, PendingSlot> PENDING_SLOTS =
            new IdentityHashMap<>();
    private static Connection clientConnection;
    private static long nextOrder;
    private static int finalServerSlot = UNKNOWN_SLOT;
    private static int acceptedServerSlot = UNKNOWN_SLOT;
    private static int silentIntentSlot = UNKNOWN_SLOT;
    private static int silentIntentTick = NO_TICK;

    private CarriedItemSync() {
    }

    public static synchronized void observeClientConnection(Connection connection) {
        if (connection == null || connection == clientConnection) return;
        clientConnection = connection;
        resetState();
    }

    public static synchronized void onIncomingPacket(Connection connection, Packet<?> packet) {
        observeClientConnection(connection);
        if (packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket) {
            resetState();
            return;
        }
        if (packet instanceof ClientboundSetCarriedItemPacket carriedItem) {
            int slot = validSlot(carriedItem.getSlot());
            if (slot == UNKNOWN_SLOT) return;
            PENDING_SLOTS.clear();
            finalServerSlot = slot;
            acceptedServerSlot = slot;
            clearSilentIntent();
            reconcileVanillaCache(ClientBase.mc == null ? null : ClientBase.mc.gameMode, slot);
        }
    }

    public static synchronized void onOutgoingPacketAccepted(
            Connection connection,
            Packet<?> packet) {
        observeClientConnection(connection);
        if (!(packet instanceof ServerboundSetCarriedItemPacket carriedItem)) return;
        int slot = validSlot(carriedItem.getSlot());
        if (slot == UNKNOWN_SLOT) return;

        PENDING_SLOTS.put(packet, new PendingSlot(++nextOrder, slot));
        acceptedServerSlot = slot;
        LocalPlayer player = ClientBase.mc == null ? null : ClientBase.mc.player;
        int visibleSlot = selectedSlot(player);
        if (visibleSlot != UNKNOWN_SLOT && visibleSlot != slot) {
            silentIntentSlot = slot;
            silentIntentTick = player.tickCount;
        } else {
            clearSilentIntent();
        }
        reconcileVanillaCache(ClientBase.mc == null ? null : ClientBase.mc.gameMode, slot);
    }

    public static synchronized void onFinalPacketWrite(
            Connection connection,
            Packet<?> packet) {
        observeClientConnection(connection);
        if (!(packet instanceof ServerboundSetCarriedItemPacket carriedItem)) return;
        int slot = validSlot(carriedItem.getSlot());
        if (slot == UNKNOWN_SLOT) return;
        PENDING_SLOTS.remove(packet);
        finalServerSlot = slot;
        refreshAcceptedSlotFromPending();
    }

    public static synchronized void onBufferedPacketDiscarded(Packet<?> packet) {
        if (!(packet instanceof ServerboundSetCarriedItemPacket)) return;
        PENDING_SLOTS.remove(packet);
        refreshAcceptedSlotFromPending();
        if (silentIntentSlot != acceptedServerSlot) clearSilentIntent();
        reconcileVanillaCache(
                ClientBase.mc == null ? null : ClientBase.mc.gameMode,
                acceptedServerSlot);
    }

    /** Called after cancellable packet hooks but before buffering/final dispatch. */
    public static synchronized void beforeOutgoingInteraction(
            Connection connection,
            Packet<?> packet) {
        observeClientConnection(connection);
        if (!usesCarriedItem(packet)) return;

        LocalPlayer player = ClientBase.mc == null ? null : ClientBase.mc.player;
        int visibleSlot = selectedSlot(player);
        if (player == null || player.connection == null || visibleSlot == UNKNOWN_SLOT) return;

        if (acceptedServerSlot == visibleSlot) {
            clearSilentIntent();
            reconcileVanillaCache(ClientBase.mc.gameMode, visibleSlot);
            return;
        }
        if (hasFreshSilentIntent(player.tickCount)) {
            clearSilentIntent();
            reconcileVanillaCache(ClientBase.mc.gameMode, acceptedServerSlot);
            return;
        }

        ServerboundSetCarriedItemPacket correction =
                new ServerboundSetCarriedItemPacket(visibleSlot);
        PacketUtil.prepareDirectSend(correction);
        try {
            player.connection.send(correction);
        } catch (Throwable throwable) {
            PacketUtil.cancelDirectSend(correction);
            throw throwable;
        }
    }

    public static void reconcileVanillaCache(MultiPlayerGameMode gameMode, int slot) {
        if (validSlot(slot) == UNKNOWN_SLOT || gameMode == null) return;
        ReflectionUtil.setCarriedIndex(gameMode, slot);
    }

    public static int vanillaCachedSlot(MultiPlayerGameMode gameMode) {
        return ReflectionUtil.getCarriedIndex(gameMode);
    }

    static synchronized int acceptedServerSlot() {
        return acceptedServerSlot;
    }

    static synchronized int finalServerSlot() {
        return finalServerSlot;
    }

    private static boolean usesCarriedItem(Packet<?> packet) {
        return packet instanceof ServerboundUseItemOnPacket
                || packet instanceof ServerboundUseItemPacket
                || packet instanceof ServerboundInteractPacket
                || packet instanceof ServerboundPlayerActionPacket;
    }

    private static boolean hasFreshSilentIntent(int currentTick) {
        if (silentIntentSlot == UNKNOWN_SLOT
                || silentIntentSlot != acceptedServerSlot
                || silentIntentTick == NO_TICK) {
            return false;
        }
        int age = currentTick - silentIntentTick;
        return age >= 0 && age <= SILENT_INTENT_MAX_AGE_TICKS;
    }

    private static int selectedSlot(LocalPlayer player) {
        return player == null ? UNKNOWN_SLOT : validSlot(player.getInventory().selected);
    }

    private static int validSlot(int slot) {
        return slot >= 0 && slot <= 8 ? slot : UNKNOWN_SLOT;
    }

    private static void refreshAcceptedSlotFromPending() {
        PendingSlot latest = null;
        for (PendingSlot pending : PENDING_SLOTS.values()) {
            if (latest == null || pending.order() > latest.order()) latest = pending;
        }
        acceptedServerSlot = latest == null ? finalServerSlot : latest.slot();
    }

    private static void clearSilentIntent() {
        silentIntentSlot = UNKNOWN_SLOT;
        silentIntentTick = NO_TICK;
    }

    private static void resetState() {
        PENDING_SLOTS.clear();
        nextOrder = 0L;
        finalServerSlot = UNKNOWN_SLOT;
        acceptedServerSlot = UNKNOWN_SLOT;
        clearSilentIntent();
    }

    private record PendingSlot(long order, int slot) {
    }
}
