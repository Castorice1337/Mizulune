package shit.zen.modules.impl.movement.scaffold.v2.motion;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import shit.zen.ClientBase;
import shit.zen.utils.misc.PacketUtil;

/**
 * Scaffold-scoped outgoing packet buffer. It is deliberately independent of
 * LagManager because Scaffold Blink queues movement and interaction packets,
 * not keep-alives.
 */
public final class ScaffoldPacketBuffer extends ClientBase {
    private final Deque<BufferedPacket> queue = new ArrayDeque<>();
    private final BiConsumer<Packet<?>, PacketSendListener> sender;
    private Blink policy = new Blink();
    private boolean flushing;

    public ScaffoldPacketBuffer() {
        this(ScaffoldPacketBuffer::sendQueued);
    }

    ScaffoldPacketBuffer(BiConsumer<Packet<?>, PacketSendListener> sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    public synchronized boolean intercept(
            Packet<?> packet,
            PacketSendListener listener,
            Blink.Settings settings,
            Blink.PacketContext context,
            long nowMillis) {
        if (packet == null || settings == null || context == null || this.flushing) {
            return false;
        }
        if (!settings.enabled()) {
            this.flush();
            return false;
        }
        if (!isBufferable(packet)) {
            return false;
        }

        Blink.Decision decision = this.policy.decide(context, settings, nowMillis);
        if (decision.action() == Blink.Action.FLUSH) {
            this.flush();
            return false;
        }
        this.queue.addLast(new BufferedPacket(packet, listener));
        return true;
    }

    public synchronized void onBlockPlacement(Blink.Settings settings) {
        if (settings != null && settings.enabled()) {
            this.policy.onBlockPlacement(settings);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public synchronized void flush() {
        if (this.flushing) {
            return;
        }
        this.flushing = true;
        try {
            while (!this.queue.isEmpty()) {
                BufferedPacket buffered = this.queue.removeFirst();
                this.sender.accept(buffered.packet(), buffered.listener());
            }
        } finally {
            this.flushing = false;
        }
    }

    public synchronized void reset() {
        this.flush();
        this.policy = new Blink();
    }

    public synchronized void discard() {
        this.queue.clear();
        this.policy = new Blink();
    }

    public synchronized int size() {
        return this.queue.size();
    }

    public synchronized boolean isFlushing() {
        return this.flushing;
    }

    static boolean isBufferable(Packet<?> packet) {
        return packet instanceof ServerboundMovePlayerPacket
                || packet instanceof ServerboundUseItemPacket
                || packet instanceof ServerboundUseItemOnPacket
                || packet instanceof ServerboundSetCarriedItemPacket
                || packet instanceof ServerboundSwingPacket
                || packet instanceof ServerboundPlayerCommandPacket;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void sendQueued(Packet<?> packet, PacketSendListener listener) {
        PacketUtil.sendBuffered((Packet) packet, listener);
    }

    private record BufferedPacket(Packet<?> packet, PacketSendListener listener) {
    }
}
