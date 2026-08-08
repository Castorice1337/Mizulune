package shit.zen.network;

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.util.thread.BlockableEventLoop;
import org.slf4j.Logger;
import shit.zen.ZenClient;
import shit.zen.event.impl.ReceivePacketEvent;

/** 26.2 packet-error routing for the shared asynchronous dispatch helper. */
public final class PacketHandlerUtil {
    private PacketHandlerUtil() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T extends PacketListener> void processPacket(Logger logger, Packet<T> packet,
            T listener, BlockableEventLoop<?> loop) throws RunningOnDifferentThreadException {
        if (loop.isSameThread()) return;
        loop.executeIfPossible(() -> {
            if (!listener.isAcceptingMessages()) {
                logger.debug("Ignoring packet due to disconnection: {}", packet);
                return;
            }
            try {
                ReceivePacketEvent event = new ReceivePacketEvent((Packet<ClientGamePacketListener>) (Packet) packet);
                if (loop.isSameThread() && ZenClient.isReady()) {
                    ZenClient.instance.getEventBus().call(event);
                    if (event.isCancelled()) return;
                }
                packet.handle(listener);
            } catch (Exception exception) {
                listener.onPacketError(packet, exception);
            }
        });
        throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
    }
}
