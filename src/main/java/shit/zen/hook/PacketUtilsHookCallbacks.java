package shit.zen.hook;

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.util.thread.BlockableEventLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shit.zen.ZenClient;
import shit.zen.network.PacketHandlerUtil;

/** Shared clientbound same-thread scheduling hook for Patchify and Mixin. */
public final class PacketUtilsHookCallbacks {
    private static final Logger LOGGER = LoggerFactory.getLogger(PacketUtilsHookCallbacks.class);

    private PacketUtilsHookCallbacks() {
    }

    public static <T extends PacketListener> boolean onEnsureRunningOnSameThread(
            Packet<T> packet,
            T listener,
            BlockableEventLoop<?> loop) throws RunningOnDifferentThreadException {
        if (!ZenClient.isReady() || !(listener instanceof ClientGamePacketListener)) {
            return false;
        }
        PacketHandlerUtil.processPacket(LOGGER, packet, listener, loop);
        return true;
    }
}
